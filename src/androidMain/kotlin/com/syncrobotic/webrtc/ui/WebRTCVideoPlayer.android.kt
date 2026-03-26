package com.syncrobotic.webrtc.ui

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.config.*
import com.syncrobotic.webrtc.signaling.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import org.webrtc.SurfaceViewRenderer
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Android WebRTC Video Player supporting both WHEP HTTP and WebSocket signaling.
 * 
 * This composable handles:
 * 1. Signaling via WHEP HTTP (streaming direct) or WebSocket (custom backend)
 * 2. WebRTC PeerConnection setup
 * 3. Video rendering via SurfaceViewRenderer
 */
@Composable
internal fun WebRTCVideoPlayer(
    config: StreamConfig,
    modifier: Modifier = Modifier,
    onStateChange: OnPlayerStateChange = {},
    onEvent: OnPlayerEvent = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var connectionState by remember { mutableStateOf(WebRTCState.NEW) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }
    val connectionStartTime = remember { System.currentTimeMillis() }
    
    // Track last reported stream info to avoid redundant updates (reduce recomposition)
    var lastReportedWidth by remember { mutableStateOf(0) }
    var lastReportedHeight by remember { mutableStateOf(0) }
    var lastStreamInfoUpdateTime by remember { mutableStateOf(0L) }
    
    // Retry state
    val retryConfig = remember { config.retryConfig }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    var reconnectReason by remember { mutableStateOf<String?>(null) }
    var isReconnecting by remember { mutableStateOf(false) }
    var disconnectedDebounceJob by remember { mutableStateOf<Job?>(null) }
    
    // Track current player state for UI display
    var currentPlayerState by remember { mutableStateOf<PlayerState>(PlayerState.Idle) }
    
    // Create HTTP client for signaling
    val httpClient = remember {
        HttpClient(OkHttp) {
            install(WebSockets)
            install(HttpTimeout) {
                requestTimeoutMillis = 45_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 45_000
            }
            engine {
                config {
                    retryOnConnectionFailure(true)
                    // Trust all certificates for self-signed SSL (development only)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                        val trustAllCerts = arrayOf<TrustManager>(
                            object : X509TrustManager {
                                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                            }
                        )
                        val sslContext = SSLContext.getInstance("TLS")
                        sslContext.init(null, trustAllCerts, SecureRandom())
                        sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                        hostnameVerifier { _, _ -> true }
                    }
                }
            }
        }
    }
    
    // Signaling clients
    val whepSignaling = remember { WhepSignaling(httpClient) }
    var webSocketSignaling by remember { mutableStateOf<WebSocketSignaling?>(null) }
    
    // Create WebRTC client
    val webrtcClient = remember { WebRTCClient() }
    
    // Surface view renderer reference
    var surfaceViewRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    
    // Session resource URL for cleanup (WHEP only)
    var sessionResourceUrl by remember { mutableStateOf<String?>(null) }
    var sessionEtag by remember { mutableStateOf<String?>(null) }
    val pendingIceCandidates = remember { mutableStateListOf<CandidateInfo>() }
    var iceUfrag by remember { mutableStateOf<String?>(null) }
    var icePwd by remember { mutableStateOf<String?>(null) }
    
    // --- Reconnection helper ---
    suspend fun connectWebRTC(
        ctx: Context,
        client: WebRTCClient,
        cfg: StreamConfig,
        httpCli: HttpClient,
        whep: WhepSignaling,
        onSurfaceReady: (SurfaceViewRenderer) -> Unit,
        onResourceUrl: (String?) -> Unit,
        onWsSignaling: (WebSocketSignaling) -> Unit,
    ) {
        // Create WebRTC listener
        val listener = createWebRTCListener(
            onConnectionStateChanged = { state ->
                Log.i("WebRTCVideoPlayer", "[State] Connection state changed: $state")
                connectionState = state
                when (state) {
                    WebRTCState.CONNECTING -> {
                        currentPlayerState = PlayerState.Connecting
                        onStateChange(PlayerState.Connecting)
                    }
                    WebRTCState.CONNECTED -> {
                        disconnectedDebounceJob?.cancel()
                        disconnectedDebounceJob = null
                        errorMessage = null
                        isReconnecting = false
                        reconnectJob?.cancel()
                        reconnectReason = null  // Clear to prevent stale reconnect triggers
                        currentPlayerState = PlayerState.Playing
                        onStateChange(PlayerState.Playing)
                    }
                    WebRTCState.DISCONNECTED -> {
                        if (!isReconnecting && retryConfig.retryOnDisconnect && retryConfig.maxRetries > 0) {
                            currentPlayerState = PlayerState.Buffering()
                            onStateChange(PlayerState.Buffering())
                            disconnectedDebounceJob?.cancel()
                            disconnectedDebounceJob = scope.launch {
                                delay(5000)
                                reconnectReason = "Connection temporarily lost"
                            }
                        } else if (!isReconnecting) {
                            currentPlayerState = PlayerState.Buffering()
                            onStateChange(PlayerState.Buffering())
                        }
                    }
                    WebRTCState.FAILED -> {
                        disconnectedDebounceJob?.cancel()
                        if (!isReconnecting && retryConfig.retryOnError && retryConfig.maxRetries > 0) {
                            reconnectReason = "WebRTC connection failed"
                        } else if (!isReconnecting) {
                            errorMessage = "WebRTC connection failed"
                            currentPlayerState = PlayerState.Error("WebRTC connection failed")
                            onStateChange(PlayerState.Error("WebRTC connection failed"))
                        }
                    }
                    WebRTCState.CLOSED -> {
                        disconnectedDebounceJob?.cancel()
                        if (!isReconnecting && retryConfig.retryOnDisconnect && retryConfig.maxRetries > 0) {
                            reconnectReason = "WebRTC connection closed"
                        } else if (!isReconnecting) {
                            currentPlayerState = PlayerState.Stopped
                            onStateChange(PlayerState.Stopped)
                        }
                    }
                    else -> {}
                }
            },
            onError = { error ->
                errorMessage = error
                currentPlayerState = PlayerState.Error(error)
                onStateChange(PlayerState.Error(error))
            },
            onRemoteStreamAdded = {
                if (!hasReportedFirstFrame) {
                    hasReportedFirstFrame = true
                    val timeToFirstFrame = System.currentTimeMillis() - connectionStartTime
                    onEvent(PlayerEvent.FirstFrameRendered(timeToFirstFrame))
                }
            },
            onVideoFrame = { frame ->
                if (frame.width > 0 && frame.height > 0) {
                    val now = System.currentTimeMillis()
                    // Only send event when resolution changes or at most once per second for FPS updates
                    val resolutionChanged = frame.width != lastReportedWidth || frame.height != lastReportedHeight
                    val shouldUpdateFps = now - lastStreamInfoUpdateTime >= 1000L
                    
                    if (resolutionChanged || shouldUpdateFps) {
                        lastReportedWidth = frame.width
                        lastReportedHeight = frame.height
                        lastStreamInfoUpdateTime = now
                        
                        val streamInfo = StreamInfo(
                            width = frame.width,
                            height = frame.height,
                            protocol = "WebRTC",
                            codec = "VP8/H264",
                            fps = client.getCurrentFps()
                        )
                        onEvent(PlayerEvent.StreamInfoReceived(streamInfo))
                    }
                }
            },
            onIceCandidate = { candidate, sdpMid, sdpMLineIndex ->
                // In Full ICE mode, candidates are embedded in the SDP — skip PATCH
                if (cfg.webrtcConfig.iceMode == IceMode.FULL_ICE) return@createWebRTCListener
                val resourceUrl = sessionResourceUrl
                if (resourceUrl.isNullOrBlank()) {
                    pendingIceCandidates.add(CandidateInfo(candidate, sdpMid))
                    return@createWebRTCListener
                }
                scope.launch(Dispatchers.IO) {
                    try {
                        whep.sendIceCandidate(
                            resourceUrl = resourceUrl,
                            candidate = "a=$candidate\r\n",
                            etag = sessionEtag,
                            iceUfrag = iceUfrag,
                            icePwd = icePwd,
                            mid = sdpMid
                        )
                    } catch (e: Exception) {
                        Log.w("WebRTCVideoPlayer", "[WHEP] Failed to send ICE candidate: ${e.message}")
                    }
                }
            }
        )

        // Initialize WebRTC with Android context
        client.initializeWithContext(ctx, cfg.webrtcConfig, listener)

        // Create the SurfaceViewRenderer and attach to WebRTC
        withContext(Dispatchers.Main) {
            onSurfaceReady(client.createSurfaceViewRenderer(ctx))
        }

        // Perform signaling based on type
        withContext(Dispatchers.IO) {
            when (cfg.webrtcConfig.signalingType) {
                SignalingType.WHEP_HTTP -> {
                    performWhepSignaling(client, whep, cfg) { url, etag, ufrag, pwd ->
                        onResourceUrl(url)
                        sessionEtag = etag
                        iceUfrag = ufrag
                        icePwd = pwd
                        if (!url.isNullOrBlank() && pendingIceCandidates.isNotEmpty()) {
                            val candidates = pendingIceCandidates.toList()
                            pendingIceCandidates.clear()
                            scope.launch(Dispatchers.IO) {
                                candidates.forEach { pending ->
                                    try {
                                        whep.sendIceCandidate(
                                            resourceUrl = url,
                                            candidate = "a=${pending.candidate}\r\n",
                                            etag = sessionEtag,
                                            iceUfrag = iceUfrag,
                                            icePwd = icePwd,
                                            mid = pending.mid
                                        )
                                    } catch (e: Exception) {
                                        Log.w("WebRTCVideoPlayer", "[WHEP] Failed to send ICE candidate: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }
                SignalingType.WEBSOCKET -> {
                    val wsConfig = cfg.webrtcConfig.wsConfig
                        ?: throw IllegalStateException("WebSocket config is required for WEBSOCKET signaling type")
                    val wsSignaling = WebSocketSignaling(httpCli, wsConfig)
                    onWsSignaling(wsSignaling)
                    performWebSocketSignaling(client, wsSignaling, cfg)
                }
            }
        }
    }

    suspend fun performReconnect(reason: String) {
        // Skip if already connected
        if (connectionState == WebRTCState.CONNECTED) {
            println("WebRTCVideoPlayer: Skipping reconnect - already connected")
            return
        }
        var currentReason = reason
        while (true) {
            try {
                StreamRetryHandler.withRetry(
                    config = retryConfig,
                    actionName = "WebRTC reconnect",
                    onAttempt = { attempt, maxAttempts, delayMs ->
                        // Skip if already connected
                        if (connectionState == WebRTCState.CONNECTED) return@withRetry
                        Log.w("WebRTCVideoPlayer", "Reconnecting ($attempt/$maxAttempts) in ${delayMs}ms - $currentReason")
                        val reconnectState = PlayerState.Reconnecting(
                            attempt = attempt,
                            maxAttempts = maxAttempts,
                            reason = currentReason,
                            nextRetryMs = delayMs
                        )
                        currentPlayerState = reconnectState
                        onStateChange(PlayerState.Reconnecting(
                            attempt = attempt,
                            maxAttempts = maxAttempts,
                            reason = currentReason,
                            nextRetryMs = delayMs
                        ))
                    }
                ) { _ ->
                    surfaceViewRenderer = null
                    errorMessage = null
                    hasReportedFirstFrame = false
                    isReconnecting = true
                    sessionResourceUrl?.let { url ->
                        try { whepSignaling.terminateSession(url) } catch (_: Exception) {}
                        sessionResourceUrl = null
                    }
                    try { webrtcClient.close() } catch (_: Exception) {}
                    try { webSocketSignaling?.disconnect() } catch (_: Exception) {}

                    connectWebRTC(
                        context, webrtcClient, config, httpClient, whepSignaling,
                        onSurfaceReady = { surfaceViewRenderer = it },
                        onResourceUrl = { sessionResourceUrl = it },
                        onWsSignaling = { webSocketSignaling = it }
                    )
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("WebRTCVideoPlayer", "All reconnect attempts failed, will retry in ${retryConfig.maxDelayMs}ms", e)
                isReconnecting = false
                val waitingState = PlayerState.Reconnecting(
                    attempt = retryConfig.maxRetries,
                    maxAttempts = retryConfig.maxRetries,
                    reason = "Waiting for stream... ($currentReason)",
                    nextRetryMs = retryConfig.maxDelayMs
                )
                currentPlayerState = waitingState
                onStateChange(waitingState)
                delay(retryConfig.maxDelayMs)
                currentReason = "Retrying: ${e.message}"
            }
        }
    }

    // Reconnection trigger
    LaunchedEffect(reconnectReason) {
        val reason = reconnectReason ?: return@LaunchedEffect
        reconnectReason = null
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            performReconnect(reason)
        }
    }

    // Initialize WebRTC and connect
    LaunchedEffect(config.endpoints.webrtc, config.webrtcConfig.signalingType) {
        currentPlayerState = PlayerState.Connecting
        onStateChange(PlayerState.Connecting)

        try {
            StreamRetryHandler.withRetry(
                config = retryConfig,
                actionName = "WebRTC initial connect",
                onAttempt = { attempt, maxAttempts, delayMs ->
                    // Skip if already connected
                    if (connectionState == WebRTCState.CONNECTED) return@withRetry
                    Log.w("WebRTCVideoPlayer", "Retry initial connect ($attempt/$maxAttempts) in ${delayMs}ms")
                    val retryState = PlayerState.Reconnecting(
                        attempt = attempt,
                        maxAttempts = maxAttempts,
                        reason = "Initial connection failed",
                        nextRetryMs = delayMs
                    )
                    currentPlayerState = retryState
                    onStateChange(retryState)
                }
            ) { attempt ->
                if (attempt > 1) {
                    surfaceViewRenderer = null
                    errorMessage = null
                    hasReportedFirstFrame = false
                    isReconnecting = true
                    try { webrtcClient.close() } catch (_: Exception) {}
                    try { webSocketSignaling?.disconnect() } catch (_: Exception) {}
                }
                connectWebRTC(
                    context, webrtcClient, config, httpClient, whepSignaling,
                    onSurfaceReady = { surfaceViewRenderer = it },
                    onResourceUrl = { sessionResourceUrl = it },
                    onWsSignaling = { webSocketSignaling = it }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("WebRTCVideoPlayer", "Failed to connect", e)
            val failedState = PlayerState.Reconnecting(
                attempt = retryConfig.maxRetries,
                maxAttempts = retryConfig.maxRetries,
                reason = "Waiting for stream...",
                nextRetryMs = retryConfig.maxDelayMs
            )
            currentPlayerState = failedState
            onStateChange(failedState)
            delay(retryConfig.maxDelayMs)
            reconnectReason = "Initial connection retry: ${e.message}"
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            reconnectJob?.cancel()
            disconnectedDebounceJob?.cancel()
            webrtcClient.close()
            // Fire-and-forget WHIP/WebSocket termination using a fresh scope independent of composable lifecycle
            val urlToTerminate = sessionResourceUrl
            val wsToDisconnect = webSocketSignaling
            CoroutineScope(Dispatchers.IO).launch {
                when (config.webrtcConfig.signalingType) {
                    SignalingType.WHEP_HTTP -> {
                        urlToTerminate?.let { url ->
                            try { whepSignaling.terminateSession(url) } catch (_: Exception) {}
                        }
                    }
                    SignalingType.WEBSOCKET -> {
                        try { wsToDisconnect?.disconnect() } catch (_: Exception) {}
                    }
                }
                httpClient.close()
            }
        }
    }
    
    // Render the video
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black)
    ) {
        // Render SurfaceViewRenderer if available (even during reconnection)
        if (surfaceViewRenderer != null) {
            AndroidView(
                factory = { _ ->
                    surfaceViewRenderer!!.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Show overlay based on current player state
        when (val state = currentPlayerState) {
            is PlayerState.Connecting -> {
                ConnectionStatusOverlay(
                    message = "Connecting...",
                    showProgress = true
                )
            }
            is PlayerState.Reconnecting -> {
                ConnectionStatusOverlay(
                    message = "Reconnecting (${state.attempt}/${state.maxAttempts})...\n${state.reason}",
                    showProgress = true
                )
            }
            is PlayerState.Error -> {
                ConnectionStatusOverlay(
                    message = "Error: ${state.message}",
                    showProgress = false,
                    isError = true
                )
            }
            is PlayerState.Buffering -> {
                ConnectionStatusOverlay(
                    message = "Buffering...",
                    showProgress = true
                )
            }
            is PlayerState.Loading, is PlayerState.Idle -> {
                if (surfaceViewRenderer == null) {
                    ConnectionStatusOverlay(
                        message = if (state is PlayerState.Loading) "Loading..." else "Initializing...",
                        showProgress = true
                    )
                }
            }
            else -> {} // Playing, Paused, Stopped - no overlay needed when playing
        }
    }
}

/**
 * Overlay composable to show connection status.
 */
@Composable
private fun ConnectionStatusOverlay(
    message: String,
    showProgress: Boolean = true,
    isError: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    color = if (isError) Color.Red else Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(
                text = message,
                color = if (isError) Color.Red else Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Create a WebRTC listener with callback functions.
 */
private fun createWebRTCListener(
    onConnectionStateChanged: (WebRTCState) -> Unit,
    onError: (String) -> Unit,
    onRemoteStreamAdded: () -> Unit,
    onVideoFrame: (VideoFrame) -> Unit,
    onIceCandidate: (String, String?, Int) -> Unit
): WebRTCListener {
    return object : WebRTCListener {
        override fun onConnectionStateChanged(state: WebRTCState) = onConnectionStateChanged(state)
        override fun onError(error: String) = onError(error)
        override fun onRemoteStreamAdded() = onRemoteStreamAdded()
        override fun onIceGatheringStateChanged(state: IceGatheringState) {}
        override fun onIceConnectionStateChanged(state: IceConnectionState) {}
        override fun onIceGatheringComplete() {}
        override fun onVideoFrame(frame: VideoFrame) = onVideoFrame(frame)
        override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) = onIceCandidate(candidate, sdpMid, sdpMLineIndex)
    }
}

/**
 * Perform WHEP HTTP signaling (direct to streaming).
 */
private suspend fun performWhepSignaling(
    webrtcClient: WebRTCClient,
    whepSignaling: WhepSignaling,
    config: StreamConfig,
    onResourceUrl: (String?, String?, String?, String?) -> Unit
) {
    val initialSdpOffer = webrtcClient.createOffer(receiveVideo = true, receiveAudio = true)

    val sdpOffer = if (config.webrtcConfig.iceMode == IceMode.FULL_ICE) {
        val deadline = System.currentTimeMillis() + config.webrtcConfig.iceGatheringTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            val localSdp = webrtcClient.getLocalDescription()
            if (localSdp != null && localSdp.contains("a=end-of-candidates")) break
            delay(100)
        }
        webrtcClient.getLocalDescription() ?: initialSdpOffer
    } else {
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            val localSdp = webrtcClient.getLocalDescription()
            if (localSdp != null && localSdp.contains("a=candidate:")) break
            delay(50)
        }
        webrtcClient.getLocalDescription() ?: initialSdpOffer
    }

    val iceUfrag = extractSdpValue(sdpOffer, "a=ice-ufrag:")
    val icePwd = extractSdpValue(sdpOffer, "a=ice-pwd:")

    val result = whepSignaling.sendOffer(config.endpoints.webrtc, sdpOffer)
    val resolvedUrl = resolveWhepResourceUrl(config.endpoints.webrtc, result.resourceUrl)
    onResourceUrl(resolvedUrl, result.etag, iceUfrag, icePwd)

    webrtcClient.setRemoteAnswer(result.sdpAnswer)
}

private fun resolveWhepResourceUrl(whepUrl: String, resourceUrl: String?): String? {
    if (resourceUrl.isNullOrBlank()) return null
    if (resourceUrl.startsWith("http://") || resourceUrl.startsWith("https://")) return resourceUrl
    val schemeIndex = whepUrl.indexOf("://")
    val startIndex = if (schemeIndex >= 0) schemeIndex + 3 else 0
    val pathIndex = whepUrl.indexOf('/', startIndex)
    val origin = if (pathIndex == -1) whepUrl else whepUrl.substring(0, pathIndex)
    return if (resourceUrl.startsWith("/")) "$origin$resourceUrl" else "$origin/$resourceUrl"
}

private fun extractSdpValue(sdp: String, prefix: String): String? {
    return sdp.lineSequence()
        .firstOrNull { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.trim()
        ?.ifBlank { null }
}

private data class CandidateInfo(val candidate: String, val mid: String?)

/**
 * Perform WebSocket signaling.
 */
private suspend fun performWebSocketSignaling(
    webrtcClient: WebRTCClient,
    wsSignaling: WebSocketSignaling,
    config: StreamConfig
) {
    val streamName = config.webrtcConfig.wsConfig?.streamName ?: "raw"
    wsSignaling.connect(streamName)
    val sdpOffer = webrtcClient.createOffer(receiveVideo = true, receiveAudio = true)
    val result = wsSignaling.sendOffer(sdpOffer, streamName)
    webrtcClient.setRemoteAnswer(result.sdpAnswer)
}
