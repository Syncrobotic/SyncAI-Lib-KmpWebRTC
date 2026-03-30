@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.StreamRetryHandler
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JVM/Desktop implementation of [WhepSession].
 */
actual class WhepSession actual constructor(
    private val signaling: SignalingAdapter,
    private val config: WebRTCConfig,
    private val retryConfig: RetryConfig
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    actual val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<WebRTCStats?>(null)
    actual val stats: StateFlow<WebRTCStats?> = _stats.asStateFlow()

    internal val client = WebRTCClient()
    private var resourceUrl: String? = null

    // DataChannel: configs registered before connect(), created during SDP negotiation
    private val pendingDataChannelConfigs = mutableListOf<DataChannelConfig>()
    private val createdDataChannels = mutableMapOf<String, DataChannel>()

    /**
     * Internal callback invoked after client initialization in doConnect().
     * Used by session-based VideoRenderer to set up the video sink.
     */
    internal var onClientReady: ((WebRTCClient) -> Unit)? = null
    private var statsJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var closed = false

    actual suspend fun connect() {
        if (closed) return
        _state.value = SessionState.Connecting

        try {
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WHEP connect",
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
        client.initialize(config, object : WebRTCListener {
            override fun onConnectionStateChanged(state: WebRTCState) {
                when (state) {
                    WebRTCState.CONNECTED -> {
                        _state.value = SessionState.Connected
                        startStatsCollection()
                    }
                    WebRTCState.DISCONNECTED -> {
                        if (!closed) {
                            scope.launch { reconnect() }
                        }
                    }
                    WebRTCState.FAILED -> {
                        if (!closed) {
                            _state.value = SessionState.Error(
                                message = "WebRTC connection failed",
                                isRetryable = true
                            )
                        }
                    }
                    else -> {}
                }
            }

            override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                if (!closed && config.iceMode == com.syncrobotic.webrtc.config.IceMode.TRICKLE_ICE) {
                    resourceUrl?.let { url ->
                        scope.launch {
                            try {
                                signaling.sendIceCandidate(url, candidate, sdpMid, sdpMLineIndex)
                            } catch (_: Exception) { }
                        }
                    }
                }
            }
        })

        onClientReady?.invoke(client)

        // Create pending DataChannels before SDP offer so they're included in negotiation
        for (dcConfig in pendingDataChannelConfigs) {
            client.createDataChannel(dcConfig)?.let { createdDataChannels[dcConfig.label] = it }
        }

        val localSdp = client.createOffer(receiveVideo = true, receiveAudio = true)

        // For FULL_ICE, wait for gathering then use local description with candidates
        val offerSdp = if (config.iceMode == com.syncrobotic.webrtc.config.IceMode.FULL_ICE) {
            delay(config.iceGatheringTimeoutMs.coerceAtMost(10_000L))
            client.getLocalDescription() ?: localSdp
        } else {
            localSdp
        }

        val result = signaling.sendOffer(offerSdp)
        client.setRemoteAnswer(result.sdpAnswer)
        resourceUrl = result.resourceUrl
    }

    private suspend fun reconnect() {
        if (closed) return
        try {
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WHEP reconnect",
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

    actual fun setAudioEnabled(enabled: Boolean) {
        client.setAudioEnabled(enabled)
    }

    actual fun setSpeakerphoneEnabled(enabled: Boolean) {
        client.setSpeakerphoneEnabled(enabled)
    }

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
