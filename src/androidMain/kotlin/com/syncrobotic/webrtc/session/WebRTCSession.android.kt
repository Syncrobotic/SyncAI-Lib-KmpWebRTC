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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    // ── Public callbacks for custom implementations ───────────────────
    actual var onRemoteVideoFrame: ((frame: Any) -> Unit)? = null
    actual var onLocalVideoTrack: ((track: Any) -> Unit)? = null

    private var statsJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var closed = false

    /** Serialises attempt start/stop so two attempts never touch the client at once. */
    private val attemptMutex = Mutex()

    /** The single in-flight connect/reconnect attempt, owned by this session. */
    private var attemptJob: Job? = null

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
        runAttempt("connect")
    }

    actual suspend fun retryNow() {
        if (closed) return
        Log.d(TAG, "retryNow() called - interrupting in-flight attempt")
        runAttempt("retryNow")
    }

    /**
     * Run a connect attempt as the session's single owned attempt.
     *
     * Cancels **and joins** any previous attempt before touching the client, so a
     * "retry now" arriving mid-reconnect can never race the attempt it replaces.
     *
     * The attempt runs via [withContext] on a session-owned [Job] rather than being
     * launched on [scope], so it keeps the caller's dispatcher — retry backoff then
     * still uses the caller's scheduler, which matters for `runTest` virtual time.
     */
    private suspend fun runAttempt(actionName: String) {
        val gate = attemptMutex.withLock {
            attemptJob?.cancelAndJoin()
            _state.value = SessionState.Connecting
            Job().also { attemptJob = it }
        }
        try {
            withContext(gate) { attemptLoop(actionName) }
        } catch (e: CancellationException) {
            // Distinguish "a newer attempt superseded us" from "our caller was
            // cancelled". Only the latter should propagate.
            if (currentCoroutineContext().isActive) return else throw e
        } finally {
            gate.complete()
        }
    }

    private suspend fun attemptLoop(actionName: String) {
        try {
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WebRTCSession $actionName",
                onAttempt = { attempt, maxAttempts, _ ->
                    _state.value = SessionState.Reconnecting(attempt, maxAttempts)
                }
            ) {
                // Every attempt starts from a clean client: doConnect() re-initialises it,
                // and re-initialising over a half-built one is what stuck sessions before.
                // All calls are null-safe, so this is a no-op on a fresh session.
                cleanup(terminate = true)
                doConnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$actionName failed: ${e::class.simpleName}: ${e.message}")
            if (!closed) {
                val kind = classifySessionError(e)
                _state.value = SessionState.Error(
                    message = e.message ?: "Connection failed",
                    cause = e,
                    isRetryable = kind.isRetryable,
                    kind = kind
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
                override fun onVideoFrame(frame: VideoFrame) {
                    frame.nativeFrame?.let { nativeFrame ->
                        onRemoteVideoFrame?.invoke(nativeFrame)
                    }
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
                override fun onVideoFrame(frame: VideoFrame) {
                    frame.nativeFrame?.let { nativeFrame ->
                        onRemoteVideoFrame?.invoke(nativeFrame)
                    }
                }
            })
        }

        // Initialize camera capture if sendVideo is enabled
        if (mediaConfig.sendVideo) {
            client.initializeCameraCapture(ctx, mediaConfig.videoConfig)
            // Notify local video track is ready
            client.getLocalVideoTrack()?.let { track ->
                onLocalVideoTrack?.invoke(track)
            }
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
        // Record resourceUrl BEFORE setRemoteAnswer: the server allocated the
        // resource at this point, so close() must be able to DELETE it even
        // if setRemoteAnswer hangs.
        resourceUrl = result.resourceUrl
        client.setRemoteAnswer(result.sdpAnswer)

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
                if (!closed && attemptJob?.isActive != true) {
                    scope.launch { reconnect() }
                }
            }
            WebRTCState.FAILED -> {
                Log.d(TAG, "Failed, closed=$closed")
                if (!closed && attemptJob?.isActive != true) {
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
        if (closed) return
        Log.d(TAG, "reconnect() triggered")
        runAttempt("reconnect")
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

    actual fun setRemoteVideoEnabled(enabled: Boolean) {
        client.setRemoteVideoEnabled(enabled)
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
                // Launch on an independent scope so the DELETE request is not
                // cancelled by the upcoming scope.cancel() in close().
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
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
