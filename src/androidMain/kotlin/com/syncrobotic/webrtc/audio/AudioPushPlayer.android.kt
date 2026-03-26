package com.syncrobotic.webrtc.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.signaling.WhipSignaling
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import kotlin.math.pow
import kotlinx.coroutines.*

private const val TAG = "AudioPushPlayer"

/**
 * Android implementation of AudioPushPlayer.
 * Uses WebRTCClient for audio push via WHIP protocol.
 */
@Composable
actual fun AudioPushPlayer(
    config: AudioPushConfig,
    autoStart: Boolean,
    onStateChange: OnAudioPushStateChange
): AudioPushController {
    val context = LocalContext.current
    // No rememberCoroutineScope — client manages its own scope independently of composition
    val controller = remember(config.whipUrl) {
        AndroidAudioPushClient(
            context = context,
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
@Composable
actual fun rememberAudioPushController(
    config: AudioPushConfig,
    onStateChange: OnAudioPushStateChange
): AudioPushController {
    return AudioPushPlayer(config = config, autoStart = false, onStateChange = onStateChange)
}

/**
 * Android implementation of AudioPushClient.
 */
actual class AudioPushClient actual constructor(
    config: AudioPushConfig,
    onStateChange: OnAudioPushStateChange
) : AudioPushController {

    private val impl = AndroidAudioPushClient(
        context = null,
        config = config,
        onStateChange = onStateChange
    )

    actual override val state: AudioPushState get() = impl.state
    actual override val stats: WebRTCStats? get() = impl.stats

    actual override fun start() = impl.start()
    actual override fun stop() = impl.stop()
    actual override fun setMuted(muted: Boolean) = impl.setMuted(muted)
    actual override suspend fun refreshStats() = impl.refreshStats()
    actual fun release() = impl.release()
}

/**
 * Internal Android audio push controller implementation.
 */
internal class AndroidAudioPushClient(
    private val context: Context?,
    private val config: AudioPushConfig,
    private val onStateChange: OnAudioPushStateChange
) : AudioPushController {

    // Client owns its own scope — independent of any composable lifecycle
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

        // Check microphone permission if context is available
        context?.let { ctx ->
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                updateState(AudioPushState.Error(
                    message = "Microphone permission not granted",
                    isRetryable = false
                ))
                return
            }
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
        resourceUrl?.let { url ->
            try {
                whipSignaling?.terminateSession(url)
            } catch (e: Exception) {
                // Ignore errors on cleanup
            }
        }
        resourceUrl = null

        webrtcClient?.close()
        webrtcClient = null

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
                android.util.Log.e(TAG, "Connection attempt ${retryAttempt + 1} failed: ${e.message}", e)

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

                val delayMs = (config.retryConfig.initialDelayMs *
                    config.retryConfig.multiplier.pow((retryAttempt - 1).toDouble()))
                    .toLong()
                    .coerceAtMost(config.retryConfig.maxDelayMs)

                delay(delayMs)
                cleanup()
            }
        }
    }

    private suspend fun connect() {
        httpClient = HttpClient(OkHttp)
        whipSignaling = WhipSignaling(httpClient!!)

        val client = WebRTCClient()
        webrtcClient = client

        val ctx = context ?: throw IllegalStateException("Context is required for audio push on Android")

        client.initializeForSending(
            context = ctx,
            config = config.webrtcConfig,
            listener = object : WebRTCListener {
                override fun onConnectionStateChanged(state: WebRTCState) {
                    android.util.Log.i(TAG, "Connection state: $state")
                    when (state) {
                        WebRTCState.CONNECTED -> {
                            retryAttempt = 0
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
                    android.util.Log.i(TAG, "ICE gathering state: $state")
                }

                override fun onError(error: String) {
                    android.util.Log.e(TAG, "WebRTC error: $error")
                    handleConnectionFailure(error)
                }
            }
        )

        val sdpOffer = client.createSendOffer(sendVideo = false, sendAudio = true)
        android.util.Log.i(TAG, "Created SDP offer (length=${sdpOffer.length})")

        delay(config.webrtcConfig.iceGatheringTimeoutMs.coerceAtMost(5000))

        val completeSdp = client.getLocalDescription() ?: sdpOffer
        android.util.Log.i(TAG, "Complete SDP with ICE (length=${completeSdp.length})")

        val result = whipSignaling?.sendOffer(config.whipUrl, completeSdp)
            ?: throw Exception("WHIP signaling not initialized")

        android.util.Log.i(TAG, "WHIP answer received (length=${result.sdpAnswer.length})")
        resourceUrl = result.resourceUrl

        client.setRemoteAnswer(result.sdpAnswer)
        android.util.Log.i(TAG, "Audio push connection established")

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
                    android.util.Log.w(TAG, "Failed to collect stats: ${e.message}")
                }
                delay(1000)
            }
        }
    }
}
