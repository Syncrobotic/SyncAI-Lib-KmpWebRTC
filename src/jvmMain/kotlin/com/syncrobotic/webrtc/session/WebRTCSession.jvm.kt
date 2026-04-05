@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.config.*
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JVM/Desktop implementation of [WebRTCSession].
 */
actual class WebRTCSession actual constructor(
    private val signaling: SignalingAdapter,
    private val mediaConfig: MediaConfig,
    private val webrtcConfig: WebRTCConfig,
    private val retryConfig: RetryConfig
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    actual val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<WebRTCStats?>(null)
    actual val stats: StateFlow<WebRTCStats?> = _stats.asStateFlow()

    internal val client = WebRTCClient()
    private var resourceUrl: String? = null
    private var muted = false

    // DataChannel: configs registered before connect(), created during SDP negotiation
    private val pendingDataChannelConfigs = mutableListOf<DataChannelConfig>()
    private val createdDataChannels = mutableMapOf<String, DataChannel>()

    /**
     * Internal callback invoked after client initialization.
     * Used by VideoRenderer to set up video sink, and CameraPreview for local video.
     */
    internal var onClientReady: ((WebRTCClient) -> Unit)? = null

    private var statsJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var closed = false

    @Volatile
    private var isReconnecting = false

    actual suspend fun connect() {
        if (closed) return
        _state.value = SessionState.Connecting

        try {
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WebRTCSession connect",
                onAttempt = { attempt, maxAttempts, _ ->
                    _state.value = SessionState.Reconnecting(attempt, maxAttempts)
                }
            ) {
                doConnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!closed) {
                _state.value = SessionState.Error(
                    message = e.message ?: "Connection failed",
                    cause = e,
                    isRetryable = true
                )
            }
        }
    }

    private suspend fun doConnect() {
        createdDataChannels.clear()

        // Initialize client: use initializeForSending if we need to send audio
        if (mediaConfig.requiresSending) {
            client.initializeForSending(webrtcConfig, object : WebRTCListener {
                override fun onConnectionStateChanged(state: WebRTCState) {
                    handleConnectionState(state)
                }
                override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                    handleIceCandidate(candidate, sdpMid, sdpMLineIndex)
                }
            })
        } else {
            client.initialize(webrtcConfig, object : WebRTCListener {
                override fun onConnectionStateChanged(state: WebRTCState) {
                    handleConnectionState(state)
                }
                override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                    handleIceCandidate(candidate, sdpMid, sdpMLineIndex)
                }
            })
        }

        // Initialize camera capture if sendVideo is enabled
        if (mediaConfig.sendVideo) {
            client.initializeCameraCapture(mediaConfig.videoConfig)
        }

        onClientReady?.invoke(client)

        // Create pending DataChannels before SDP offer
        for (dcConfig in pendingDataChannelConfigs) {
            client.createDataChannel(dcConfig)?.let { createdDataChannels[dcConfig.label] = it }
        }

        // Create offer with flexible media directions
        val localSdp = client.createFlexibleOffer(mediaConfig)

        // For FULL_ICE, wait for gathering then use local description with candidates
        val offerSdp = if (webrtcConfig.iceMode == IceMode.FULL_ICE) {
            delay(webrtcConfig.iceGatheringTimeoutMs.coerceAtMost(10_000L))
            client.getLocalDescription() ?: localSdp
        } else {
            localSdp
        }

        val result = signaling.sendOffer(offerSdp)
        client.setRemoteAnswer(result.sdpAnswer)
        resourceUrl = result.resourceUrl

        // Apply initial mute state
        if (muted && mediaConfig.sendAudio) {
            client.setAudioEnabled(false)
        }
    }

    private fun handleConnectionState(state: WebRTCState) {
        when (state) {
            WebRTCState.CONNECTED -> {
                _state.value = SessionState.Connected
                startStatsCollection()
            }
            WebRTCState.DISCONNECTED, WebRTCState.FAILED -> {
                if (!closed && !isReconnecting) {
                    scope.launch { reconnect() }
                }
            }
            else -> {}
        }
    }

    private fun handleIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        if (!closed && webrtcConfig.iceMode == IceMode.TRICKLE_ICE) {
            resourceUrl?.let { url ->
                scope.launch {
                    try {
                        signaling.sendIceCandidate(url, candidate, sdpMid, sdpMLineIndex)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    private suspend fun reconnect() {
        if (closed || isReconnecting) return
        isReconnecting = true
        try {
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WebRTCSession reconnect",
                onAttempt = { attempt, maxAttempts, _ ->
                    _state.value = SessionState.Reconnecting(attempt, maxAttempts)
                }
            ) {
                cleanup(terminate = true)
                doConnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!closed) {
                _state.value = SessionState.Error(
                    message = e.message ?: "Reconnection failed",
                    cause = e,
                    isRetryable = false
                )
            }
        } finally {
            isReconnecting = false
        }
    }

    actual fun createDataChannel(config: DataChannelConfig): DataChannel? {
        createdDataChannels[config.label]?.let { return it }
        if (!client.isConnected && client.connectionState == WebRTCState.NEW) {
            pendingDataChannelConfigs.add(config)
            return null
        }
        return client.createDataChannel(config)?.also { createdDataChannels[config.label] = it }
    }

    // ── Receive-side controls ─────────────────────────────────────────

    actual fun setAudioEnabled(enabled: Boolean) {
        // Controls incoming audio playback (speaker)
        client.setAudioEnabled(enabled)
    }

    actual fun setSpeakerphoneEnabled(enabled: Boolean) {
        client.setSpeakerphoneEnabled(enabled)
    }

    // ── Send-side controls ────────────────────────────────────────────

    actual fun setMuted(muted: Boolean) {
        this.muted = muted
        if (mediaConfig.sendAudio) {
            client.setAudioEnabled(!muted)
        }
    }

    actual fun toggleMute() {
        setMuted(!muted)
    }

    actual fun setVideoEnabled(enabled: Boolean) {
        if (mediaConfig.sendVideo) {
            client.setVideoEnabled(enabled)
        }
    }

    actual fun switchCamera() {
        if (mediaConfig.sendVideo) {
            client.switchCamera()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    actual fun close() {
        if (closed) return
        closed = true
        _state.value = SessionState.Closed
        cleanup(terminate = true)
        scope.cancel()
    }

    private fun cleanup(terminate: Boolean) {
        statsJob?.cancel()
        statsJob = null
        if (terminate) {
            resourceUrl?.let { url ->
                scope.launch {
                    try { signaling.terminate(url) } catch (_: Exception) { }
                }
            }
        }
        client.close()
        resourceUrl = null
    }

    private fun startStatsCollection() {
        statsJob?.cancel()
        statsJob = scope.launch {
            while (isActive && !closed) {
                try {
                    _stats.value = client.getStats()
                } catch (_: Exception) { }
                delay(1000)
            }
        }
    }
}
