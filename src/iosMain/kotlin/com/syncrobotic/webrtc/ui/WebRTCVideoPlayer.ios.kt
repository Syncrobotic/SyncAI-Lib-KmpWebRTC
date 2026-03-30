@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.ui

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
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cocoapods.GoogleWebRTC.*
import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.config.*
import com.syncrobotic.webrtc.signaling.*
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.*
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.*

/**
 * Get current time in milliseconds.
 */
private fun currentTimeMillis(): Long = 
    (NSDate().timeIntervalSince1970 * 1000).toLong()

/**
 * iOS WebRTC Video Player supporting both WHEP HTTP and WebSocket signaling.
 * 
 * This composable handles:
 * 1. Signaling via WHEP HTTP (streaming direct) or WebSocket (custom backend)
 * 2. WebRTC PeerConnection setup
 * 3. Video rendering via RTCMTLVideoView (Metal-based)
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal fun WebRTCVideoPlayer(
    config: StreamConfig,
    modifier: Modifier = Modifier,
    onStateChange: OnPlayerStateChange = {},
    onEvent: OnPlayerEvent = {}
) {
    val scope = rememberCoroutineScope()
    
    var connectionState by remember { mutableStateOf<WebRTCState>(WebRTCState.NEW) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }
    val connectionStartTime = remember { currentTimeMillis() }
    
    // Track last reported stream info to avoid redundant updates (reduce recomposition)
    var lastReportedWidth by remember { mutableStateOf(0) }
    var lastReportedHeight by remember { mutableStateOf(0) }
    var lastStreamInfoUpdateTime by remember { mutableStateOf(0L) }
    
    // Track current player state for UI display
    var currentPlayerState by remember { mutableStateOf<PlayerState>(PlayerState.Idle) }
    
    // Retry state
    val retryConfig = remember { config.retryConfig }
    var reconnectJob by remember { mutableStateOf<Job?>(null) }
    var reconnectReason by remember { mutableStateOf<String?>(null) }
    var isReconnecting by remember { mutableStateOf(false) }
    var disconnectedDebounceJob by remember { mutableStateOf<Job?>(null) }
    
    // Create HTTP client for signaling
    val httpClient = remember {
        HttpClient(Darwin) {
            install(WebSockets)
            install(HttpTimeout) {
                requestTimeoutMillis = 45_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 45_000
            }
            engine {
                configureRequest {
                    setAllowsCellularAccess(true)
                }
                // Note: For self-signed certificates, configure your iOS app's 
                // Info.plist with NSAppTransportSecurity settings
            }
        }
    }
    
    // Signaling clients
    val whepSignaling = remember { WhepSignaling(httpClient) }
    var webSocketSignaling by remember { mutableStateOf<WebSocketSignaling?>(null) }
    
    // Create WebRTC client
    val webrtcClient = remember { WebRTCClient() }
    
    // Video view reference
    var videoView by remember { mutableStateOf<RTCMTLVideoView?>(null) }
    
    // Session resource URL for cleanup (WHEP only)
    var sessionResourceUrl by remember { mutableStateOf<String?>(null) }
    var sessionEtag by remember { mutableStateOf<String?>(null) }
    val pendingIceCandidates = remember { mutableStateListOf<CandidateInfo>() }
    var iceUfrag by remember { mutableStateOf<String?>(null) }
    var icePwd by remember { mutableStateOf<String?>(null) }
    
    // --- Reconnection helper ---
    suspend fun connectWebRTC(
        client: WebRTCClient,
        cfg: StreamConfig,
        httpCli: HttpClient,
        whep: WhepSignaling,
        onVideoViewReady: (RTCMTLVideoView) -> Unit,
        onResourceUrl: (String?) -> Unit,
        onWsSignaling: (WebSocketSignaling) -> Unit,
    ) {
        val listener = object : WebRTCListener {
            override fun onConnectionStateChanged(state: WebRTCState) {
                println("🔗 [WebRTCVideoPlayer] [State] Connection state changed: $state (isReconnecting=$isReconnecting)")
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
                                println("🔗 [WebRTCVideoPlayer] [State] DISCONNECTED debounce: waiting 5s for ICE recovery...")
                                delay(5000)
                                println("🔗 [WebRTCVideoPlayer] [State] DISCONNECTED debounce expired, triggering reconnect")
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
            }

            override fun onError(error: String) {
                errorMessage = error
                currentPlayerState = PlayerState.Error(error)
                onStateChange(PlayerState.Error(error))
            }

            override fun onRemoteStreamAdded() {
                if (!hasReportedFirstFrame) {
                    hasReportedFirstFrame = true
                    val timeToFirstFrame = currentTimeMillis() - connectionStartTime
                    onEvent(PlayerEvent.FirstFrameRendered(timeToFirstFrame))
                }
            }

            override fun onIceGatheringStateChanged(state: IceGatheringState) {
                println("🔗 [WebRTCVideoPlayer] [ICE] Gathering state: $state")
            }

            override fun onIceConnectionStateChanged(state: IceConnectionState) {
                println("🔗 [WebRTCVideoPlayer] [ICE] Connection state: $state")
            }

            override fun onIceGatheringComplete() {
                println("🔗 [WebRTCVideoPlayer] [ICE] Gathering complete")
            }

            override fun onVideoFrame(frame: VideoFrame) {
                if (frame.width > 0 && frame.height > 0) {
                    val now = currentTimeMillis()
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
            }

            override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
                // In Full ICE mode, candidates are embedded in the SDP — skip PATCH
                if (cfg.webrtcConfig.iceMode == IceMode.FULL_ICE) return
                val resourceUrl = sessionResourceUrl
                if (resourceUrl.isNullOrBlank()) {
                    pendingIceCandidates.add(CandidateInfo(candidate, sdpMid))
                    return
                }
                scope.launch(Dispatchers.Default) {
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
                        println("[WebRTCVideoPlayer] [WHEP] Failed to send ICE candidate: ${e.message}")
                    }
                }
            }
        }

        client.initialize(cfg.webrtcConfig, listener)
        onVideoViewReady(client.createVideoView())

        withContext(Dispatchers.Default) {
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
                            scope.launch(Dispatchers.Default) {
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
                                        println("[WebRTCVideoPlayer] [WHEP] Failed to send ICE candidate: ${e.message}")
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
                        println("WebRTCVideoPlayer: Reconnecting ($attempt/$maxAttempts) in ${delayMs}ms - $currentReason")
                        val reconnectState = PlayerState.Reconnecting(
                            attempt = attempt,
                            maxAttempts = maxAttempts,
                            reason = currentReason,
                            nextRetryMs = delayMs
                        )
                        currentPlayerState = reconnectState
                        onStateChange(reconnectState)
                    }
                ) {
                    videoView = null
                    errorMessage = null
                    hasReportedFirstFrame = false
                    isReconnecting = true
                    sessionResourceUrl?.let { url ->
                        try { whepSignaling.terminateSession(url) } catch (_: Exception) {}
                        sessionResourceUrl = null
                    }
                    try { webrtcClient.close() } catch (_: Exception) {}
                    try { webSocketSignaling?.disconnect() } catch (_: Exception) {}
                    isReconnecting = false
                    connectWebRTC(
                        webrtcClient, config, httpClient, whepSignaling,
                        onVideoViewReady = { videoView = it },
                        onResourceUrl = { sessionResourceUrl = it },
                        onWsSignaling = { webSocketSignaling = it }
                    )
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("WebRTCVideoPlayer: All reconnect attempts failed, will retry in ${retryConfig.maxDelayMs}ms - ${e.message}")
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
                    println("WebRTCVideoPlayer: Retry initial connect ($attempt/$maxAttempts) in ${delayMs}ms")
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
                    println("WebRTCVideoPlayer: Cleaning up before retry attempt $attempt")
                    videoView = null
                    errorMessage = null
                    hasReportedFirstFrame = false
                    try { webrtcClient.close() } catch (_: Exception) {}
                    try { webSocketSignaling?.disconnect() } catch (_: Exception) {}
                }
                connectWebRTC(
                    webrtcClient, config, httpClient, whepSignaling,
                    onVideoViewReady = { videoView = it },
                    onResourceUrl = { sessionResourceUrl = it },
                    onWsSignaling = { webSocketSignaling = it }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("WebRTCVideoPlayer: Failed to connect - ${e.message}")
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
            CoroutineScope(Dispatchers.Default).launch {
                when (config.webrtcConfig.signalingType) {
                    SignalingType.WHEP_HTTP -> {
                        urlToTerminate?.let { url ->
                            try {
                                whepSignaling.terminateSession(url)
                            } catch (e: Exception) {
                                // Ignore cleanup errors
                            }
                        }
                    }
                    SignalingType.WEBSOCKET -> {
                        try {
                            wsToDisconnect?.disconnect()
                        } catch (e: Exception) {
                            // Ignore cleanup errors
                        }
                    }
                }
                httpClient.close()
            }
        }
    }
    
    // Render UI
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Render video view if available (even during reconnection)
        if (videoView != null) {
            UIKitView(
                factory = {
                    videoView!!.apply {
                        backgroundColor = UIColor.blackColor
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { _ -> },
                onRelease = { _ -> }
            )
        }
        
        // Show overlay based on current player state
        when (val state = currentPlayerState) {
            is PlayerState.Connecting -> {
                ConnectionStatusOverlayIOS(
                    message = "Connecting...",
                    showProgress = true
                )
            }
            is PlayerState.Reconnecting -> {
                ConnectionStatusOverlayIOS(
                    message = "Reconnecting (${state.attempt}/${state.maxAttempts})...\n${state.reason}",
                    showProgress = true
                )
            }
            is PlayerState.Error -> {
                ConnectionStatusOverlayIOS(
                    message = "Error: ${state.message}",
                    showProgress = false,
                    isError = true
                )
            }
            is PlayerState.Buffering -> {
                ConnectionStatusOverlayIOS(
                    message = "Buffering...",
                    showProgress = true
                )
            }
            is PlayerState.Loading, is PlayerState.Idle -> {
                if (videoView == null) {
                    ConnectionStatusOverlayIOS(
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
 * Overlay composable to show connection status for iOS.
 */
@Composable
private fun ConnectionStatusOverlayIOS(
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
 * Perform WHEP HTTP signaling (direct connection to streaming).
 */
private suspend fun performWhepSignaling(
    webrtcClient: WebRTCClient,
    whepSignaling: WhepSignaling,
    config: StreamConfig,
    onSessionCreated: (String?, String?, String?, String?) -> Unit
) {
    val iceServers = config.webrtcConfig.iceServers.flatMap { it.urls }
    println("[WebRTCVideoPlayer] [WHEP] Config ICE servers: ${iceServers.joinToString(", ")}")
    println("[WebRTCVideoPlayer] [WHEP] Target URL: ${config.endpoints.webrtc} (iceMode=${config.webrtcConfig.iceMode})")

    // Create SDP offer
    val initialOffer = webrtcClient.createOffer(
        receiveVideo = true,
        receiveAudio = true
    )

    val offer = if (config.webrtcConfig.iceMode == IceMode.FULL_ICE) {
        println("[WebRTCVideoPlayer] [WHEP] Full ICE: waiting for ICE gathering to complete (timeout=${config.webrtcConfig.iceGatheringTimeoutMs}ms)...")
        val startTime = currentTimeMillis()
        val deadline = startTime + config.webrtcConfig.iceGatheringTimeoutMs
        while (currentTimeMillis() < deadline) {
            val localSdp = webrtcClient.getLocalDescription()
            if (localSdp != null && localSdp.contains("a=end-of-candidates")) {
                println("[WebRTCVideoPlayer] [WHEP] Full ICE: gathering complete")
                break
            }
            delay(100)
        }
        val fullSdp = webrtcClient.getLocalDescription() ?: initialOffer
        println("[WebRTCVideoPlayer] [WHEP] Full ICE: using SDP with embedded candidates (${fullSdp.length} bytes)")
        fullSdp
    } else {
        println("[WebRTCVideoPlayer] [WHEP] Trickle ICE: waiting for initial candidates (max 2s)...")
        val startTime = currentTimeMillis()
        val deadline = startTime + 2000
        while (currentTimeMillis() < deadline) {
            val localSdp = webrtcClient.getLocalDescription()
            if (localSdp != null && localSdp.contains("a=candidate:")) {
                println("[WebRTCVideoPlayer] [WHEP] Trickle ICE: found initial candidates, proceeding")
                break
            }
            delay(50)
        }
        val sdpWithInitialCandidates = webrtcClient.getLocalDescription() ?: initialOffer
        println("[WebRTCVideoPlayer] [WHEP] Trickle ICE: using SDP with initial candidates (${sdpWithInitialCandidates.length} bytes)")
        sdpWithInitialCandidates
    }

    println("[WebRTCVideoPlayer] [WHEP] SDP offer:\n$offer")
    val extractedIceUfrag = extractSdpValue(offer, "ice-ufrag")
    val extractedIcePwd = extractSdpValue(offer, "ice-pwd")

    // Send offer to WHEP endpoint
    val whepResult = whepSignaling.sendOffer(
        whepUrl = config.endpoints.webrtc,
        sdpOffer = offer
    )

    // Store session URL for cleanup
    val resolvedUrl = resolveWhepResourceUrl(config.endpoints.webrtc, whepResult.resourceUrl)
    onSessionCreated(resolvedUrl, whepResult.etag, extractedIceUfrag, extractedIcePwd)

    // Set remote answer
    webrtcClient.setRemoteAnswer(whepResult.sdpAnswer)

    println("[WebRTCVideoPlayer] [WHEP] SDP exchange complete (iceMode=${config.webrtcConfig.iceMode})")
}

private fun resolveWhepResourceUrl(whepUrl: String, resourceUrl: String?): String? {
    if (resourceUrl.isNullOrBlank()) {
        return null
    }
    if (resourceUrl.startsWith("http://") || resourceUrl.startsWith("https://")) {
        return resourceUrl
    }
    val schemeIndex = whepUrl.indexOf("://")
    val startIndex = if (schemeIndex >= 0) schemeIndex + 3 else 0
    val pathIndex = whepUrl.indexOf('/', startIndex)
    val origin = if (pathIndex == -1) whepUrl else whepUrl.substring(0, pathIndex)
    return if (resourceUrl.startsWith("/")) "$origin$resourceUrl" else "$origin/$resourceUrl"
}

private fun extractSdpValue(sdp: String, key: String): String? {
    val prefix = "a=$key:"
    return sdp.lineSequence()
        .firstOrNull { it.startsWith(prefix) }
        ?.removePrefix(prefix)
        ?.trim()
}

private data class CandidateInfo(
    val candidate: String,
    val mid: String?
)

/**
 * Perform WebSocket signaling (via custom signaling server).
 */
private suspend fun performWebSocketSignaling(
    webrtcClient: WebRTCClient,
    wsSignaling: WebSocketSignaling,
    config: StreamConfig
) {
    val wsConfig = config.webrtcConfig.wsConfig
        ?: throw IllegalStateException("WebSocket config is required")
    
    val streamName = wsConfig.streamName
    
    println("📡 iOS WebSocket: Connecting to signaling server for stream: $streamName")
    
    wsSignaling.connect(streamName)
    
    println("📡 iOS WebSocket: Connected, creating SDP offer...")
    
    val offer = webrtcClient.createOffer(
        receiveVideo = true,
        receiveAudio = true
    )
    
    println("📡 iOS WebSocket: Sending offer for stream: $streamName")
    
    val answerResult = wsSignaling.sendOffer(offer, streamName)
    
    println("📡 iOS WebSocket: Received SDP answer, setting remote description")
    
    webrtcClient.setRemoteAnswer(answerResult.sdpAnswer)
    
    println("✅ iOS WebSocket signaling completed")
}
