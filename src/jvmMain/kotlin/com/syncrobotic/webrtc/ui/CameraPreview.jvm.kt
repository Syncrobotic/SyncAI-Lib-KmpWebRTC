@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import dev.onvoid.webrtc.media.video.VideoFrame as NativeVideoFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

/**
 * JVM/Desktop implementation of [CameraPreview].
 *
 * Adds a [VideoTrackSink] to the local video track from the session's WebRTCClient,
 * converts frames to Compose [ImageBitmap], and renders them on a Canvas.
 * Optionally mirrors the image for front-camera preview.
 */
@Composable
actual fun CameraPreview(
    session: WebRTCSession,
    modifier: Modifier,
    mirror: Boolean,
    onStateChange: ((PlayerState) -> Unit)?,
) {
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    val sessionState by session.state.collectAsState()

    val frameFlow = remember { MutableSharedFlow<ImageBitmap>(replay = 1) }

    // Collect frames on main thread
    LaunchedEffect(frameFlow) {
        frameFlow.collect { bitmap -> currentFrame = bitmap }
    }

    // Set up local video sink
    LaunchedEffect(session) {
        session.onClientReady = { client ->
            currentFrame = null
            // Add sink to local video track for preview
            val localTrack = client.getLocalVideoTrack()
            localTrack?.addSink(object : dev.onvoid.webrtc.media.video.VideoTrackSink {
                override fun onVideoFrame(frame: NativeVideoFrame) {
                    val bitmap = convertLocalFrameToImageBitmap(frame)
                    if (bitmap != null) {
                        frameFlow.tryEmit(bitmap)
                    }
                }
            })
        }
        // Auto-connect if idle
        if (session.state.value == SessionState.Idle || session.state.value is SessionState.Error) {
            session.connect()
        }
    }

    // Map state changes
    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toPlayerState())
    }

    // Render
    val frame = currentFrame
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (frame != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (mirror) Modifier.graphicsLayer(scaleX = -1f) else Modifier)
            ) {
                drawImage(
                    image = frame,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(frame.width, frame.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }
        } else {
            // Placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when (sessionState) {
                    is SessionState.Connecting, is SessionState.Reconnecting -> {
                        CircularProgressIndicator(color = Color.White)
                    }
                    is SessionState.Error -> {
                        Text(
                            text = (sessionState as SessionState.Error).message,
                            color = Color.Red
                        )
                    }
                    else -> {
                        Text(text = "Camera Off", color = Color.Gray)
                    }
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(session) {
        onDispose {
            session.onClientReady = null
        }
    }
}

/**
 * Convert a webrtc-java VideoFrame to ImageBitmap (same as VideoRenderer conversion).
 */
private fun convertLocalFrameToImageBitmap(frame: NativeVideoFrame): ImageBitmap? {
    return try {
        val buffer = frame.buffer
        val i420 = buffer.toI420()
        val width = i420.width
        val height = i420.height

        val rgbaBytes = ByteArray(width * height * 4)
        val yPlane = i420.dataY
        val uPlane = i420.dataU
        val vPlane = i420.dataV
        val yStride = i420.strideY
        val uStride = i420.strideU
        val vStride = i420.strideV

        var offset = 0
        for (row in 0 until height) {
            for (col in 0 until width) {
                val y = (yPlane.get(row * yStride + col).toInt() and 0xFF)
                val u = (uPlane.get((row / 2) * uStride + (col / 2)).toInt() and 0xFF) - 128
                val v = (vPlane.get((row / 2) * vStride + (col / 2)).toInt() and 0xFF) - 128

                val r = (y + 1.402 * v).toInt().coerceIn(0, 255)
                val g = (y - 0.344136 * u - 0.714136 * v).toInt().coerceIn(0, 255)
                val b = (y + 1.772 * u).toInt().coerceIn(0, 255)

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
        null
    }
}
