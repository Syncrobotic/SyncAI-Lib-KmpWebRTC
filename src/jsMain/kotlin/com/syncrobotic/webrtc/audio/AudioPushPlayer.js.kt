@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.*
import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.session.WhipSession
import com.syncrobotic.webrtc.signaling.WhipSignaling
import io.ktor.client.*
import io.ktor.client.engine.js.*
import kotlin.math.pow
import kotlinx.coroutines.*

/**
 * JS implementation of session-based AudioPushPlayer.
 */
@Composable
actual fun AudioPushPlayer(
    session: WhipSession,
    autoStart: Boolean,
    onStateChange: ((AudioPushState) -> Unit)?,
): AudioPushController {
    val scope = rememberCoroutineScope()
    val sessionState by session.state.collectAsState()

    LaunchedEffect(session, autoStart) {
        if (autoStart && session.state.value == SessionState.Idle) {
            session.connect()
        }
    }

    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toAudioPushState())
    }

    return remember(session) { SessionAudioPushController(session, scope) }
}

/**
 * JS implementation of session-based AudioPushPlayer for [WebRTCSession].
 * Stub — full browser support not yet implemented.
 */
@Composable
actual fun AudioPushPlayer(
    session: WebRTCSession,
    autoStart: Boolean,
    onStateChange: ((AudioPushState) -> Unit)?,
): AudioPushController {
    TODO("WebRTCSession AudioPushPlayer not yet implemented for this platform")
}

/**
 * JS implementation of AudioPushPlayer (legacy config-based API).
 */
@Suppress("DEPRECATION")
@Composable
actual fun AudioPushPlayer(
    config: AudioPushConfig,
    autoStart: Boolean,
    onStateChange: OnAudioPushStateChange
): AudioPushController {
    val controller = remember(config.whipUrl) {
        JsAudioPushClient(
            config = config,
            onStateChange = onStateChange
        )
    }
    
    // Auto start if requested
    LaunchedEffect(autoStart) {
        if (autoStart) {
            controller.start()
        }
    }
    
    // Cleanup on disposal
    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }
    
    return controller
}

/**
 * Remember an AudioPushController with automatic lifecycle management.
 */
@Suppress("DEPRECATION")
@Composable
actual fun rememberAudioPushController(
    config: AudioPushConfig,
    onStateChange: OnAudioPushStateChange
): AudioPushController {
    return AudioPushPlayer(config = config, autoStart = false, onStateChange = onStateChange)
}

/**
 * JavaScript implementation of AudioPushClient.
 */
actual class AudioPushClient actual constructor(
    config: AudioPushConfig,
    onStateChange: OnAudioPushStateChange
) : AudioPushController {
    
    private val impl = JsAudioPushClient(
        config = config,
        onStateChange = onStateChange
    )
    
    actual override val state: AudioPushState get() = impl.state
    actual override val stats: WebRTCStats? get() = impl.stats
    
    actual override fun start() = impl.start()
    actual override fun stop() = impl.stop()
    actual override fun setMuted(muted: Boolean) = impl.setMuted(muted)
    actual override suspend fun refreshStats() = impl.refreshStats()
    @Deprecated(
        message = "Use close() instead for consistent naming with Session API. Will be removed in v3.0.",
        replaceWith = ReplaceWith("close()")
    )
    actual fun release() = impl.release()

    actual fun close() {
        @Suppress("DEPRECATION")
        release()
    }
}

/**
 * Internal JS audio push controller implementation.
 */
internal class JsAudioPushClient(
    private val config: AudioPushConfig,
    private val onStateChange: OnAudioPushStateChange
) : AudioPushController {

    // Client owns its own scope — independent of any composable lifecycle
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var webrtcClient: WebRTCClient? = null
    private var whipSignaling: WhipSignaling? = null
    private var httpClient: HttpClient? = null
    private var resourceUrl: String? = null
    private var connectionJob: Job? = null
    private var statsJob: Job? = null
    private var retryAttempt = 0

    private var _state: AudioPushState = AudioPushState.Idle
    override val state: AudioPushState get() = _state

    private var _stats: WebRTCStats? = null
    override val stats: WebRTCStats? get() = _stats

    private fun log(message: String) {
        console.log("[AudioPushPlayer] $message")
    }

    private fun updateState(newState: AudioPushState) {
        _state = newState
        onStateChange(newState)
    }

    override suspend fun refreshStats() {
        _stats = webrtcClient?.getStats()
    }
    
    override fun start() {
        if (_state is AudioPushState.Streaming || _state is AudioPushState.Connecting) {
            return
        }
        
        // Check if browser supports getUserMedia
        val navigator = js("navigator")
        val mediaDevices = navigator.mediaDevices
        if (mediaDevices == null || mediaDevices == undefined) {
            updateState(AudioPushState.Error(
                message = "Browser does not support audio capture (mediaDevices API not available)",
                isRetryable = false
            ))
            return
        }
        
        retryAttempt = 0
        updateState(AudioPushState.Connecting)
        
        connectionJob = clientScope.launch {
            connectWithRetry()
        }
    }
    
    override fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        statsJob?.cancel()
        statsJob = null

        // clientScope is always active (not tied to composable), so launch is guaranteed to run
        clientScope.launch {
            cleanup()
            updateState(AudioPushState.Disconnected)
        }
    }
    
    override fun setMuted(muted: Boolean) {
        webrtcClient?.setAudioEnabled(!muted)
        if (_state is AudioPushState.Streaming || _state is AudioPushState.Muted) {
            updateState(if (muted) AudioPushState.Muted else AudioPushState.Streaming)
        }
    }
    
    fun release() {
        connectionJob?.cancel()
        statsJob?.cancel()
        // Close WebRTC synchronously to release microphone immediately
        webrtcClient?.close()
        webrtcClient = null
        // Terminate WHIP session in a fire-and-forget coroutine before cancelling scope
        val resourceUrlToTerminate = resourceUrl
        resourceUrl = null
        if (resourceUrlToTerminate != null) {
            clientScope.launch {
                try {
                    whipSignaling?.terminateSession(resourceUrlToTerminate)
                } catch (e: Exception) {
                    // Ignore errors on cleanup
                }
                httpClient?.close()
                httpClient = null
                whipSignaling = null
            }
        } else {
            httpClient?.close()
            httpClient = null
            whipSignaling = null
        }
        _stats = null
        clientScope.cancel()
    }
    
    private suspend fun cleanup() {
        // Terminate WHIP session
        resourceUrl?.let { url ->
            try {
                whipSignaling?.terminateSession(url)
            } catch (e: Exception) {
                // Ignore errors on cleanup
            }
        }
        resourceUrl = null

        // Close WebRTC
        webrtcClient?.close()
        webrtcClient = null
        
        // Close HTTP client
        httpClient?.close()
        httpClient = null
        whipSignaling = null
        
        _stats = null
    }
    
    private suspend fun connectWithRetry() {
        while (retryAttempt <= config.retryConfig.maxAttempts) {
            try {
                connect()
                return // Success
            } catch (e: Exception) {
                log("Connection attempt ${retryAttempt + 1} failed: ${e.message}")
                console.error(e)
                
                if (retryAttempt >= config.retryConfig.maxAttempts) {
                    updateState(AudioPushState.Error(
                        message = e.message ?: "Connection failed",
                        cause = e,
                        isRetryable = false
                    ))
                    return
                }
                
                retryAttempt++
                updateState(AudioPushState.Reconnecting(
                    attempt = retryAttempt,
                    maxAttempts = config.retryConfig.maxAttempts
                ))
                
                // Calculate delay with exponential backoff
                val delayMs = (config.retryConfig.initialDelayMs * 
                    config.retryConfig.multiplier.pow((retryAttempt - 1).toDouble()))
                    .toLong()
                    .coerceAtMost(config.retryConfig.maxDelayMs)
                
                delay(delayMs)
                
                // Cleanup before retry
                cleanup()
            }
        }
    }
    
    private suspend fun connect() {
        // Initialize HTTP client for WHIP signaling
        httpClient = HttpClient(Js)
        whipSignaling = WhipSignaling(httpClient!!)
        
        // Initialize WebRTC client
        val client = WebRTCClient()
        webrtcClient = client
        
        client.initialize(
            config = config.webrtcConfig,
            listener = object : WebRTCListener {
                override fun onConnectionStateChanged(state: WebRTCState) {
                    log("Connection state: $state")
                    when (state) {
                        WebRTCState.CONNECTED -> {
                            retryAttempt = 0 // Reset retry count on success
                            if (_state !is AudioPushState.Muted) {
                                updateState(AudioPushState.Streaming)
                            }
                        }
                        WebRTCState.FAILED -> {
                            handleConnectionFailure("WebRTC connection failed")
                        }
                        WebRTCState.DISCONNECTED -> {
                            if (_state is AudioPushState.Streaming || _state is AudioPushState.Muted) {
                                handleConnectionFailure("Connection lost")
                            }
                        }
                        else -> {}
                    }
                }
                
                override fun onIceGatheringStateChanged(state: IceGatheringState) {
                    log("ICE gathering state: $state")
                }
                
                override fun onError(error: String) {
                    log("WebRTC error: $error")
                    handleConnectionFailure(error)
                }
            }
        )
        
        // Create SDP offer for sending audio
        val sdpOffer = client.createSendOffer(sendVideo = false, sendAudio = true)
        log("Created SDP offer (length=${sdpOffer.length})")
        
        // Wait for ICE gathering
        delay(config.webrtcConfig.iceGatheringTimeoutMs.coerceAtMost(5000))
        
        // Get complete SDP with ICE candidates
        val completeSdp = client.getLocalDescription() ?: sdpOffer
        log("Complete SDP with ICE (length=${completeSdp.length})")
        
        // Send offer to WHIP endpoint
        val result = whipSignaling?.sendOffer(config.whipUrl, completeSdp)
            ?: throw Exception("WHIP signaling not initialized")
        
        log("WHIP answer received (length=${result.sdpAnswer.length})")
        resourceUrl = result.resourceUrl
        
        // Set remote answer
        client.setRemoteAnswer(result.sdpAnswer)

        log("Audio push connection established")

        // Start periodic stats collection
        startStatsCollection()
    }
    
    private fun handleConnectionFailure(message: String) {
        if (config.retryConfig.maxAttempts > 0 && retryAttempt < config.retryConfig.maxAttempts) {
            connectionJob?.cancel()
            connectionJob = clientScope.launch {
                retryAttempt++
                updateState(AudioPushState.Reconnecting(
                    attempt = retryAttempt,
                    maxAttempts = config.retryConfig.maxAttempts
                ))
                cleanup()
                connectWithRetry()
            }
        } else {
            updateState(AudioPushState.Error(message = message, isRetryable = false))
        }
    }

    private fun startStatsCollection() {
        statsJob?.cancel()
        statsJob = clientScope.launch {
            while (isActive && (_state is AudioPushState.Streaming || _state is AudioPushState.Muted)) {
                try {
                    refreshStats()
                } catch (e: Exception) {
                    log("Failed to collect stats: ${e.message}")
                }
                delay(1000)
            }
        }
    }
}
