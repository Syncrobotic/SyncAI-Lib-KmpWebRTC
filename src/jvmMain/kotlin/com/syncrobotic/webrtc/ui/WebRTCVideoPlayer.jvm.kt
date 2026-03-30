@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.syncrobotic.webrtc.*
import com.syncrobotic.webrtc.config.*
import com.syncrobotic.webrtc.signaling.*
import dev.onvoid.webrtc.media.video.VideoFrame as NativeVideoFrame
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

/**
 * JVM/Desktop WebRTC Video Player supporting both WHEP HTTP and WebSocket signaling.
 * 
 * This composable handles:
 * 1. Signaling via WHEP HTTP (streaming direct) or WebSocket (custom backend)
 * 2. WebRTC PeerConnection setup using webrtc-java library
 * 3. Video rendering via Compose Canvas with Skia bitmap conversion
 */
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
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }
    var hasReportedStreamInfo by remember { mutableStateOf(false) }
    val connectionStartTime = remember { System.currentTimeMillis() }
    
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
        HttpClient(CIO) {
            install(WebSockets)
            install(HttpTimeout) {
                requestTimeoutMillis = 45_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 45_000
            }
            engine {
                https {
                    trustManager = createTrustAllManager()
                }
            }
        }
    }
    
    // Signaling clients
    val whepSignaling = remember { WhepSignaling(httpClient) }
    var webSocketSignaling by remember { mutableStateOf<WebSocketSignaling?>(null) }
    
    // Create WebRTC client
    val webrtcClient = remember { WebRTCClient() }
    
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
        onResourceUrl: (String?) -> Unit,
        onWsSignaling: (WebSocketSignaling) -> Unit,
    ) {
        val listener = createWebRTCListener(
            onConnectionStateChanged = { state ->
                println("[WebRTCVideoPlayer] [State] Connection state changed: $state (isReconnecting=$isReconnecting)")
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
                                println("[WebRTCVideoPlayer] [State] DISCONNECTED debounce: waiting 5s for ICE recovery...")
                                delay(5000)
                                println("[WebRTCVideoPlayer] [State] DISCONNECTED debounce expired, triggering reconnect")
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
                val nativeFrame = frame.nativeFrame as? NativeVideoFrame
                if (nativeFrame != null) {
                    val bitmap = convertVideoFrameToImageBitmap(nativeFrame)
                    currentFrame = bitmap
                    // Only report StreamInfo when resolution changes or at most once per second
                    if (frame.width > 0 && frame.height > 0) {
                        val currentTime = System.currentTimeMillis()
                        val dimensionsChanged = frame.width != lastReportedWidth || frame.height != lastReportedHeight
                        val timeSinceLastUpdate = currentTime - lastStreamInfoUpdateTime
                        
                        if (dimensionsChanged || timeSinceLastUpdate >= 1000) {
                            lastReportedWidth = frame.width
                            lastReportedHeight = frame.height
                            lastStreamInfoUpdateTime = currentTime
                            
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
            },
            onIceCandidate = { candidate, sdpMid, sdpMLineIndex ->
                if (cfg.webrtcConfig.iceMode == IceMode.FULL_ICE) return@createWebRTCListener
                println("[WebRTCVideoPlayer] [WHEP] ICE candidate: ${candidate.take(120)}")
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
                        println("[WebRTCVideoPlayer] [WHEP] Failed to send ICE candidate: ${e.message}")
                    }
                }
            }
        )

        client.initialize(cfg.webrtcConfig, listener)

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
            println("[WebRTCVideoPlayer] Skipping reconnect - already connected")
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
                        println("[WebRTCVideoPlayer] Reconnecting ($attempt/$maxAttempts) in ${delayMs}ms - $currentReason")
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
                    currentFrame = null
                    errorMessage = null
                    hasReportedFirstFrame = false
                    hasReportedStreamInfo = false
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
                        onResourceUrl = { sessionResourceUrl = it },
                        onWsSignaling = { webSocketSignaling = it }
                    )
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[WebRTCVideoPlayer] All reconnect attempts failed, will retry in ${retryConfig.maxDelayMs}ms: ${e.message}")
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
                    println("[WebRTCVideoPlayer] Retry initial connect ($attempt/$maxAttempts) in ${delayMs}ms")
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
                    println("[WebRTCVideoPlayer] Cleaning up before retry attempt $attempt")
                    currentFrame = null
                    errorMessage = null
                    hasReportedFirstFrame = false
                    hasReportedStreamInfo = false
                    try { webrtcClient.close() } catch (_: Exception) {}
                    try { webSocketSignaling?.disconnect() } catch (_: Exception) {}
                }
                connectWebRTC(
                    webrtcClient, config, httpClient, whepSignaling,
                    onResourceUrl = { sessionResourceUrl = it },
                    onWsSignaling = { webSocketSignaling = it }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("[WebRTCVideoPlayer] Failed to connect: ${e.message}")
            e.printStackTrace()
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
    
    // Render the video
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Always render the video frame (if available) as the base layer
        currentFrame?.let { frame ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val frameWidth = frame.width.toFloat()
                val frameHeight = frame.height.toFloat()

                val scaleX = canvasWidth / frameWidth
                val scaleY = canvasHeight / frameHeight
                val scale = kotlin.math.min(scaleX, scaleY)

                val scaledWidth = (frameWidth * scale).toInt()
                val scaledHeight = (frameHeight * scale).toInt()

                val offsetX = ((canvasWidth - scaledWidth) / 2).toInt()
                val offsetY = ((canvasHeight - scaledHeight) / 2).toInt()

                drawImage(
                    image = frame,
                    dstOffset = IntOffset(offsetX, offsetY),
                    dstSize = IntSize(scaledWidth, scaledHeight)
                )
            }
        }
        
        // Show connection status overlay based on current player state
        ConnectionStatusOverlayJVM(
            playerState = currentPlayerState,
            hasVideoFrame = currentFrame != null
        )
    }
}

/**
 * Overlay composable to show connection status with loading indicators.
 */
@Composable
private fun ConnectionStatusOverlayJVM(
    playerState: PlayerState,
    hasVideoFrame: Boolean
) {
    // Only show overlay when not playing, or when reconnecting/error states
    val shouldShowOverlay = when (playerState) {
        is PlayerState.Playing -> false
        is PlayerState.Paused -> false
        is PlayerState.Idle -> !hasVideoFrame
        is PlayerState.Connecting -> true
        is PlayerState.Loading -> true
        is PlayerState.Reconnecting -> true
        is PlayerState.Error -> true
        is PlayerState.Buffering -> !hasVideoFrame
        is PlayerState.Stopped -> !hasVideoFrame
    }
    
    if (!shouldShowOverlay) return
    
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (playerState) {
                is PlayerState.Connecting -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connecting...",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                is PlayerState.Reconnecting -> {
                    CircularProgressIndicator(color = Color.Yellow)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reconnecting (${playerState.attempt}/${playerState.maxAttempts})",
                        color = Color.Yellow,
                        textAlign = TextAlign.Center
                    )
                    playerState.reason?.let { reason ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = reason,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                is PlayerState.Error -> {
                    Text(
                        text = "Error",
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playerState.message ?: "Unknown error",
                        color = Color.Red.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                is PlayerState.Buffering -> {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Buffering...",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    CircularProgressIndicator(color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading...",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Convert WebRTC VideoFrame to Compose ImageBitmap.
 */
private fun convertVideoFrameToImageBitmap(frame: NativeVideoFrame): ImageBitmap {
    val buffer = frame.buffer
    val width = buffer.width
    val height = buffer.height
    
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.OPAQUE))
    
    val i420Buffer = buffer.toI420()
    
    try {
        val bgraData = convertI420ToBGRA(
            i420Buffer.dataY,
            i420Buffer.dataU,
            i420Buffer.dataV,
            i420Buffer.strideY,
            i420Buffer.strideU,
            i420Buffer.strideV,
            width,
            height
        )
        
        bitmap.installPixels(bgraData)
        
    } finally {
        i420Buffer.release()
    }
    
    return bitmap.asComposeImageBitmap()
}

/**
 * Convert I420 (YUV420) to BGRA format.
 */
private fun convertI420ToBGRA(
    dataY: ByteBuffer,
    dataU: ByteBuffer,
    dataV: ByteBuffer,
    strideY: Int,
    strideU: Int,
    strideV: Int,
    width: Int,
    height: Int
): ByteArray {
    val bgraData = ByteArray(width * height * 4)
    
    for (y in 0 until height) {
        for (x in 0 until width) {
            val yIndex = y * strideY + x
            val uvIndex = (y / 2) * strideU + (x / 2)
            
            val yValue = (dataY.get(yIndex).toInt() and 0xFF)
            val uValue = (dataU.get(uvIndex).toInt() and 0xFF) - 128
            val vValue = (dataV.get(uvIndex).toInt() and 0xFF) - 128
            
            var r = (yValue + 1.402 * vValue).toInt()
            var g = (yValue - 0.344136 * uValue - 0.714136 * vValue).toInt()
            var b = (yValue + 1.772 * uValue).toInt()
            
            r = r.coerceIn(0, 255)
            g = g.coerceIn(0, 255)
            b = b.coerceIn(0, 255)
            
            val pixelIndex = (y * width + x) * 4
            bgraData[pixelIndex] = b.toByte()
            bgraData[pixelIndex + 1] = g.toByte()
            bgraData[pixelIndex + 2] = r.toByte()
            bgraData[pixelIndex + 3] = 0xFF.toByte()
        }
    }
    
    return bgraData
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
        override fun onConnectionStateChanged(state: WebRTCState) {
            onConnectionStateChanged(state)
        }
        
        override fun onError(error: String) {
            onError(error)
        }
        
        override fun onRemoteStreamAdded() {
            onRemoteStreamAdded()
        }

        override fun onIceGatheringStateChanged(state: IceGatheringState) {
            println("[WebRTCVideoPlayer] [ICE] Gathering state: $state")
        }

        override fun onIceConnectionStateChanged(state: IceConnectionState) {
            println("[WebRTCVideoPlayer] [ICE] Connection state: $state")
        }

        override fun onIceGatheringComplete() {
            println("[WebRTCVideoPlayer] [ICE] Gathering complete")
        }
        
        override fun onVideoFrame(frame: VideoFrame) {
            onVideoFrame(frame)
        }

        override fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
            onIceCandidate(candidate, sdpMid, sdpMLineIndex)
        }
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
    val iceServers = config.webrtcConfig.iceServers.flatMap { it.urls }
    println("[WebRTCVideoPlayer] [WHEP] Config ICE servers: ${iceServers.joinToString(", ")}")
    println("[WebRTCVideoPlayer] [WHEP] Target URL: ${config.endpoints.webrtc} (iceMode=${config.webrtcConfig.iceMode})")

    val initialSdpOffer = webrtcClient.createOffer(
        receiveVideo = true,
        receiveAudio = true
    )

    val sdpOffer = if (config.webrtcConfig.iceMode == IceMode.FULL_ICE) {
        println("[WebRTCVideoPlayer] [WHEP] Full ICE: waiting for ICE gathering to complete (timeout=${config.webrtcConfig.iceGatheringTimeoutMs}ms)...")
        val deadline = System.currentTimeMillis() + config.webrtcConfig.iceGatheringTimeoutMs
        while (System.currentTimeMillis() < deadline) {
            val localSdp = webrtcClient.getLocalDescription()
            if (localSdp != null && localSdp.contains("a=end-of-candidates")) {
                println("[WebRTCVideoPlayer] [WHEP] Full ICE: gathering complete")
                break
            }
            delay(100)
        }
        val fullSdp = webrtcClient.getLocalDescription() ?: initialSdpOffer
        println("[WebRTCVideoPlayer] [WHEP] Full ICE: using SDP with embedded candidates (${fullSdp.length} bytes)")
        fullSdp
    } else {
        initialSdpOffer
    }

    println("[WebRTCVideoPlayer] [WHEP] SDP offer:\n$sdpOffer")
    val extractedIceUfrag = extractSdpValue(sdpOffer, "ice-ufrag")
    val extractedIcePwd = extractSdpValue(sdpOffer, "ice-pwd")

    println("[WebRTCVideoPlayer] [WHEP] Created SDP offer (${sdpOffer.length} bytes), sending to ${config.endpoints.webrtc}")

    val result = whepSignaling.sendOffer(config.endpoints.webrtc, sdpOffer)
    val resolvedUrl = resolveWhepResourceUrl(config.endpoints.webrtc, result.resourceUrl)
    onResourceUrl(resolvedUrl, result.etag, extractedIceUfrag, extractedIcePwd)

    println("[WebRTCVideoPlayer] [WHEP] Received SDP answer (${result.sdpAnswer.length} bytes), resourceUrl=${result.resourceUrl}")

    webrtcClient.setRemoteAnswer(result.sdpAnswer)

    println("[WebRTCVideoPlayer] [WHEP] SDP exchange complete, waiting for ICE connectivity...")
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
 * Perform WebSocket signaling.
 */
private suspend fun performWebSocketSignaling(
    webrtcClient: WebRTCClient,
    wsSignaling: WebSocketSignaling,
    config: StreamConfig
) {
    val streamName = config.webrtcConfig.wsConfig?.streamName ?: "raw"
    
    println("[WebRTCVideoPlayer] [WebSocket] Connecting to signaling server for stream: $streamName")
    
    wsSignaling.connect(streamName)
    
    println("[WebRTCVideoPlayer] [WebSocket] Connected, creating SDP offer...")
    
    val sdpOffer = webrtcClient.createOffer(
        receiveVideo = true,
        receiveAudio = true
    )
    
    println("[WebRTCVideoPlayer] [WebSocket] Sending offer for stream: $streamName")
    
    val result = wsSignaling.sendOffer(sdpOffer, streamName)
    
    println("[WebRTCVideoPlayer] [WebSocket] Received SDP answer, setting remote description")
    
    webrtcClient.setRemoteAnswer(result.sdpAnswer)
    
    println("[WebRTCVideoPlayer] [WebSocket] WebRTC connection established")
}

/**
 * Create a TrustManager that trusts all certificates.
 * ⚠️ DEVELOPMENT ONLY - Remove in production!
 */
private fun createTrustAllManager(): X509TrustManager {
    return object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }
}
