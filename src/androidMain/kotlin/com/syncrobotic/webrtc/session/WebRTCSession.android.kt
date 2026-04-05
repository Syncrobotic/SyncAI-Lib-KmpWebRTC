@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.session

import android.content.Context
import android.util.Log
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
 * Android implementation of [WebRTCSession].
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
    private var context: Context? = null
    private var muted = false

    // DataChannel: configs registered before connect(), created during SDP negotiation
    private val pendingDataChannelConfigs = mutableListOf<DataChannelConfig>()
    private val createdDataChannels = mutableMapOf<String, DataChannel>()

    /**
     * Internal callback invoked after client initialization in doConnect().
     * Used by session-based VideoRenderer to set up the video rendering surface.
     */
    internal var onClientReady: ((WebRTCClient, Context) -> Unit)? = null

    private var statsJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var closed = false

    @Volatile
    private var isReconnecting = false

    /**
     * Set Android Context for WebRTC initialization.
     * Must be called before [connect] on Android.
     */
    fun setContext(context: Context) {
        this.context = context.applicationContext
    }

    actual suspend fun connect() {
        if (closed) return
        Log.d(TAG, "connect() called, retryConfig=$retryConfig")
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
            Log.e(TAG, "connect() failed: ${e::class.simpleName}: ${e.message}")
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
        val ctx = context ?: throw IllegalStateException(
            "Android Context not set. Call setContext() before connect()."
        )

        Log.d(TAG, "doConnect() starting...")
        createdDataChannels.clear()

        // Initialize client: use initializeForSending if we need to send audio,
        // otherwise use initializeWithContext for receive-only
        if (mediaConfig.requiresSending) {
            client.initializeForSending(ctx, webrtcConfig, object : WebRTCListener {
                override fun onConnectionStateChanged(state: WebRTCState) {
                    handleConnectionState(state)
                }
                override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                    handleIceCandidate(candidate, sdpMid, sdpMLineIndex)
                }
            })
        } else {
            client.initializeWithContext(ctx, webrtcConfig, object : WebRTCListener {
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
            client.initializeCameraCapture(ctx, mediaConfig.videoConfig)
        }

        onClientReady?.invoke(client, ctx)

        // Create pending DataChannels before SDP offer so they're included in negotiation
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
        Log.d(TAG, "WebRTC state: $state")
        when (state) {
            WebRTCState.CONNECTED -> {
                Log.d(TAG, "Connected successfully")
                _state.value = SessionState.Connected
                startStatsCollection()
            }
            WebRTCState.DISCONNECTED -> {
                Log.d(TAG, "Disconnected, closed=$closed")
                if (!closed && !isReconnecting) {
                    scope.launch { reconnect() }
                }
            }
            WebRTCState.FAILED -> {
                Log.d(TAG, "Failed, closed=$closed")
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
        Log.d(TAG, "reconnect() triggered")
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
            Log.e(TAG, "reconnect() failed: ${e::class.simpleName}: ${e.message}")
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
        // If already created during connect(), return the existing channel
        createdDataChannels[config.label]?.let { return it }
        // If PC not ready yet, store config to be created before SDP offer
        if (!client.isConnected && client.connectionState == WebRTCState.NEW) {
            pendingDataChannelConfigs.add(config)
            return null
        }
        // Post-connect creation (requires renegotiation — may not work with WHIP/WHEP)
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

    private companion object {
        const val TAG = "WebRTCSession"
    }
}
