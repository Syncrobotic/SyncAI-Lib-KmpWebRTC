@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.audio.AudioPushConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.StreamRetryHandler
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JVM/Desktop implementation of [WhipSession].
 */
actual class WhipSession actual constructor(
    private val signaling: SignalingAdapter,
    private val audioConfig: AudioPushConfig,
    private val retryConfig: RetryConfig
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    actual val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<WebRTCStats?>(null)
    actual val stats: StateFlow<WebRTCStats?> = _stats.asStateFlow()

    private val client = WebRTCClient()
    private var resourceUrl: String? = null
    private var statsJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var muted = false

    // DataChannel: configs registered before connect(), created during SDP negotiation
    private val pendingDataChannelConfigs = mutableListOf<DataChannelConfig>()
    private val createdDataChannels = mutableMapOf<String, DataChannel>()

    @Volatile
    private var closed = false

    @Volatile
    private var isReconnecting = false

    actual suspend fun connect() {
        if (closed) return
        println("[WhipSession] [JVM] connect() called, retryConfig=$retryConfig")
        _state.value = SessionState.Connecting

        try {
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WHIP connect",
                onAttempt = { attempt, maxAttempts, _ ->
                    _state.value = SessionState.Reconnecting(attempt, maxAttempts)
                }
            ) {
                doConnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[WhipSession] [JVM] connect() failed: ${e::class.simpleName}: ${e.message}")
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
        println("[WhipSession] [JVM] doConnect() starting...")
        createdDataChannels.clear()
        client.initializeForSending(audioConfig.webrtcConfig, object : WebRTCListener {
            override fun onConnectionStateChanged(state: WebRTCState) {
                println("[WhipSession] [JVM] WebRTC state: $state")
                when (state) {
                    WebRTCState.CONNECTED -> {
                        println("[WhipSession] [JVM] Connected successfully")
                        _state.value = SessionState.Connected
                        startStatsCollection()
                    }
                    WebRTCState.DISCONNECTED -> {
                        println("[WhipSession] [JVM] Disconnected, closed=$closed")
                        if (!closed && !isReconnecting) {
                            scope.launch { reconnect() }
                        }
                    }
                    WebRTCState.FAILED -> {
                        println("[WhipSession] [JVM] Failed, closed=$closed")
                        if (!closed && !isReconnecting) {
                            scope.launch { reconnect() }
                        }
                    }
                    else -> {}
                }
            }

            override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                if (!closed && audioConfig.webrtcConfig.iceMode == com.syncrobotic.webrtc.config.IceMode.TRICKLE_ICE) {
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

        // Create pending DataChannels before SDP offer so they're included in negotiation
        for (dcConfig in pendingDataChannelConfigs) {
            client.createDataChannel(dcConfig)?.let { createdDataChannels[dcConfig.label] = it }
        }

        val localSdp = client.createSendOffer(sendVideo = false, sendAudio = true)

        val offerSdp = if (audioConfig.webrtcConfig.iceMode == com.syncrobotic.webrtc.config.IceMode.FULL_ICE) {
            delay(audioConfig.webrtcConfig.iceGatheringTimeoutMs.coerceAtMost(10_000L))
            client.getLocalDescription() ?: localSdp
        } else {
            localSdp
        }

        val result = signaling.sendOffer(offerSdp)
        client.setRemoteAnswer(result.sdpAnswer)
        resourceUrl = result.resourceUrl

        // Apply initial mute state
        if (muted) {
            client.setAudioEnabled(false)
        }
    }

    private suspend fun reconnect() {
        if (closed || isReconnecting) return
        isReconnecting = true
        println("[WhipSession] [JVM] reconnect() triggered")
        try {
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WHIP reconnect",
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
            println("[WhipSession] [JVM] reconnect() failed: ${e::class.simpleName}: ${e.message}")
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

    actual fun setMuted(muted: Boolean) {
        this.muted = muted
        client.setAudioEnabled(!muted)
    }

    actual fun toggleMute() {
        setMuted(!muted)
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
