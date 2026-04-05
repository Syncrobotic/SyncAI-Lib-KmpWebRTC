@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.syncrobotic.webrtc.VideoFrame
import com.syncrobotic.webrtc.config.StreamConfig
import com.syncrobotic.webrtc.config.StreamProtocol
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.session.WhepSession
import dev.onvoid.webrtc.media.video.VideoFrame as NativeVideoFrame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer

/**
 * JVM/Desktop implementation of session-based VideoRenderer.
 *
 * Auto-connects the [WhepSession], registers a video sink on the session's
 * internal WebRTC client, converts frames to Compose [ImageBitmap], and
 * renders them on a [Canvas].
 */
@Composable
actual fun VideoRenderer(
    session: WhepSession,
    modifier: Modifier,
    onStateChange: ((PlayerState) -> Unit)?,
    onEvent: ((PlayerEvent) -> Unit)?,
): VideoPlayerController {
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val sessionState by session.state.collectAsState()
    val connectionStartTime = remember { System.currentTimeMillis() }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }
    var lastReportedWidth by remember { mutableStateOf(0) }
    var lastReportedHeight by remember { mutableStateOf(0) }

    // SharedFlow to bridge WebRTC native thread → Compose main thread
    val frameFlow = remember { MutableSharedFlow<ImageBitmap>(replay = 1) }

    // Collect frames on the main thread to update Compose state
    LaunchedEffect(frameFlow) {
        frameFlow.collect { bitmap ->
            currentFrame = bitmap
        }
    }

    // Set up video sink and auto-connect
    LaunchedEffect(session) {
        session.onClientReady = { client ->
            // Reset state for new connection (reconnect scenario)
            hasReportedFirstFrame = false
            currentFrame = null

            client.setVideoSink(object : dev.onvoid.webrtc.media.video.VideoTrackSink {
                override fun onVideoFrame(frame: NativeVideoFrame) {
                    val bitmap = convertVideoFrameToImageBitmap(frame)
                    if (bitmap != null) {
                        frameFlow.tryEmit(bitmap)
                    }
                    // Fire FirstFrameRendered once
                    if (!hasReportedFirstFrame) {
                        hasReportedFirstFrame = true
                        val elapsed = System.currentTimeMillis() - connectionStartTime
                        onEvent?.invoke(PlayerEvent.FirstFrameRendered(elapsed))
                    }
                    // Fire StreamInfoReceived on resolution change
                    val w = frame.buffer.width
                    val h = frame.buffer.height
                    if (w > 0 && h > 0 && (w != lastReportedWidth || h != lastReportedHeight)) {
                        lastReportedWidth = w
                        lastReportedHeight = h
                        onEvent?.invoke(PlayerEvent.StreamInfoReceived(
                            StreamInfo(
                                width = w,
                                height = h,
                                protocol = "WebRTC",
                                codec = "VP8/H264",
                                fps = client.getCurrentFps()
                            )
                        ))
                    }
                }
            })
        }
        if (session.state.value == SessionState.Idle || session.state.value is SessionState.Error) {
            session.connect()
        }
    }

    // Map SessionState → PlayerState
    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toPlayerState())
    }

    // Render video (or placeholder) with status overlay
    val frame = currentFrame
    Box(modifier = modifier.fillMaxSize()) {
        if (frame != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = frame,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(frame.width, frame.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
            // Show overlay on top of last frame when not connected
            SessionStatusOverlay(sessionState)
        } else {
            SessionVideoPlaceholder(sessionState, Modifier)
        }
    }

    // Cleanup
    DisposableEffect(session) {
        onDispose {
            session.onClientReady = null
            session.client.setVideoSink(null)
        }
    }

    return remember(session) { SessionVideoPlayerController(session) }
}

/**
 * JVM/Desktop implementation of VideoRenderer backed by [WebRTCSession].
 */
@Composable
actual fun VideoRenderer(
    session: WebRTCSession,
    modifier: Modifier,
    onStateChange: ((PlayerState) -> Unit)?,
    onEvent: ((PlayerEvent) -> Unit)?,
): VideoPlayerController {
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val sessionState by session.state.collectAsState()
    val connectionStartTime = remember { System.currentTimeMillis() }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }
    var lastReportedWidth by remember { mutableStateOf(0) }
    var lastReportedHeight by remember { mutableStateOf(0) }

    val frameFlow = remember { MutableSharedFlow<ImageBitmap>(replay = 1) }

    LaunchedEffect(frameFlow) {
        frameFlow.collect { bitmap -> currentFrame = bitmap }
    }

    LaunchedEffect(session) {
        session.onClientReady = { client ->
            hasReportedFirstFrame = false
            currentFrame = null

            client.setVideoSink(object : dev.onvoid.webrtc.media.video.VideoTrackSink {
                override fun onVideoFrame(frame: NativeVideoFrame) {
                    val bitmap = convertVideoFrameToImageBitmap(frame)
                    if (bitmap != null) frameFlow.tryEmit(bitmap)
                    if (!hasReportedFirstFrame) {
                        hasReportedFirstFrame = true
                        val elapsed = System.currentTimeMillis() - connectionStartTime
                        onEvent?.invoke(PlayerEvent.FirstFrameRendered(elapsed))
                    }
                    val w = frame.buffer.width
                    val h = frame.buffer.height
                    if (w > 0 && h > 0 && (w != lastReportedWidth || h != lastReportedHeight)) {
                        lastReportedWidth = w
                        lastReportedHeight = h
                        onEvent?.invoke(PlayerEvent.StreamInfoReceived(
                            StreamInfo(width = w, height = h, protocol = "WebRTC", codec = "VP8/H264", fps = client.getCurrentFps())
                        ))
                    }
                }
            })
        }
        if (session.state.value == SessionState.Idle || session.state.value is SessionState.Error) {
            session.connect()
        }
    }

    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toPlayerState())
    }

    val frame = currentFrame
    Box(modifier = modifier.fillMaxSize()) {
        if (frame != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = frame,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(frame.width, frame.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
            SessionStatusOverlay(sessionState)
        } else {
            SessionVideoPlaceholder(sessionState, Modifier)
        }
    }

    DisposableEffect(session) {
        onDispose {
            session.onClientReady = null
            session.client.setVideoSink(null)
        }
    }

    return remember(session) { WebRTCSessionVideoPlayerController(session) }
}

/**
 * JVM/Desktop implementation of VideoRenderer (legacy config-based API).
 */
@Suppress("DEPRECATION")
@Composable
actual fun VideoRenderer(
    config: StreamConfig,
    modifier: Modifier,
    onStateChange: OnPlayerStateChange,
    onEvent: OnPlayerEvent
) {
    // Route WebRTC streams to dedicated WebRTC player
    @Suppress("DEPRECATION")
    if (config.protocol == StreamProtocol.WEBRTC) {
        WebRTCVideoPlayer(
            config = config,
            modifier = modifier,
            onStateChange = onStateChange,
            onEvent = onEvent
        )
        return
    }
    
    // For HLS/RTSP: show unsupported message
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Protocol ${config.protocol.name} not supported on JVM without FFmpeg.\nUse WebRTC protocol instead.",
            color = Color.White
        )
        
        LaunchedEffect(Unit) {
            onStateChange(PlayerState.Error("${config.protocol.name} not supported on JVM. Use WebRTC protocol."))
        }
    }
}

/**
 * Desktop implementation of VideoPlayerController.
 */
private class DesktopVideoPlayerController(
    private val scope: CoroutineScope
) : VideoPlayerController {
    
    private var playbackJob: Job? = null
    private var _isPlaying = false
    private var _currentPosition = 0L
    private var _duration = 0L
    
    override fun play() {
        _isPlaying = true
    }
    
    override fun pause() {
        _isPlaying = false
    }
    
    override fun stop() {
        _isPlaying = false
        playbackJob?.cancel()
    }
    
    override fun seekTo(positionMs: Long) {
        _currentPosition = positionMs
    }
    
    override val currentPosition: Long
        get() = _currentPosition
    
    override val duration: Long
        get() = _duration
    
    override val isPlaying: Boolean
        get() = _isPlaying
}

@Suppress("DEPRECATION")
@Composable
actual fun rememberVideoPlayerController(config: StreamConfig): VideoPlayerController {
    val scope = rememberCoroutineScope()
    return remember { DesktopVideoPlayerController(scope) }
}

/**
 * Convert a webrtc-java VideoFrame to a Compose ImageBitmap via Skia Bitmap.
 */
private fun convertVideoFrameToImageBitmap(frame: NativeVideoFrame): ImageBitmap? {
    return try {
        val buffer = frame.buffer
        val i420 = buffer.toI420()
        val width = i420.width
        val height = i420.height

        val rgbaSize = width * height * 4
        val rgbaBytes = ByteArray(rgbaSize)

        // YUV to RGBA conversion
        val yPlane = i420.dataY
        val uPlane = i420.dataU
        val vPlane = i420.dataV
        val yStride = i420.strideY
        val uStride = i420.strideU
        val vStride = i420.strideV

        var offset = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                val yIndex = row * yStride + col
                val uIndex = (row / 2) * uStride + (col / 2)
                val vIndex = (row / 2) * vStride + (col / 2)

                val y = (yPlane.get(yIndex).toInt() and 0xFF)
                val u = (uPlane.get(uIndex).toInt() and 0xFF) - 128
                val v = (vPlane.get(vIndex).toInt() and 0xFF) - 128

                val r = (y + 1.402 * v).toInt().coerceIn(0, 255)
                val g = (y - 0.344136 * u - 0.714136 * v).toInt().coerceIn(0, 255)
                val b = (y + 1.772 * u).toInt().coerceIn(0, 255)

                // Skia N32 = BGRA on most platforms
                rgbaBytes[offset++] = b.toByte()
                rgbaBytes[offset++] = g.toByte()
                rgbaBytes[offset++] = r.toByte()
                rgbaBytes[offset++] = 0xFF.toByte()
            }
        }

        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo.makeN32(width, height, ColorAlphaType.OPAQUE))
        bitmap.installPixels(bitmap.imageInfo, rgbaBytes, width * 4)

        i420.release()

        bitmap.asComposeImageBitmap()
    } catch (e: Exception) {
        println("[VideoRenderer] convertVideoFrameToImageBitmap error: ${e::class.simpleName}: ${e.message}")
        null
    }
}
