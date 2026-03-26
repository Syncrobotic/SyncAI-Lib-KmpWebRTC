package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.syncrobotic.webrtc.config.StreamConfig

/**
 * A cross-platform video renderer component.
 * 
 * This composable renders video from a stream URL using the platform-specific
 * video player implementation:
 * - Android: ExoPlayer (Media3) for HLS/RTSP, WebRTC via SurfaceViewRenderer
 * - iOS: AVPlayer (HLS), WebRTC via RTCMTLVideoView
 * - Desktop (JVM): JavaCPP FFmpeg for HLS/RTSP, WebRTC via webrtc-java
 * - Web (JS/WasmJS): HTML5 video element
 * 
 * Usage:
 * ```kotlin
 * VideoRenderer(
 *     config = StreamConfig(
 *         endpoints = ServerEndpoints.create("192.168.1.100", "raw"),
 *         protocol = StreamProtocol.WEBRTC,
 *         showControls = false
 *     ),
 *     modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f),
 *     onStateChange = { state ->
 *         when (state) {
 *             is PlayerState.Playing -> println("Playing")
 *             is PlayerState.Error -> println("Error: ${state.message}")
 *             else -> {}
 *         }
 *     },
 *     onEvent = { event ->
 *         when (event) {
 *             is PlayerEvent.StreamInfoReceived -> println("Stream: ${event.info}")
 *             else -> {}
 *         }
 *     }
 * )
 * ```
 * 
 * @param config Stream configuration including URL, protocol, and display options
 * @param modifier Compose modifier for sizing and positioning
 * @param onStateChange Callback for player state changes
 * @param onEvent Callback for player events (info, stats)
 */
@Composable
expect fun VideoRenderer(
    config: StreamConfig,
    modifier: Modifier = Modifier,
    onStateChange: OnPlayerStateChange = {},
    onEvent: OnPlayerEvent = {}
)

/**
 * Interface for controlling a video player programmatically.
 * Obtain an instance through [rememberVideoPlayerController].
 */
interface VideoPlayerController {
    /** Start or resume playback */
    fun play()
    
    /** Pause playback */
    fun pause()
    
    /** Stop playback and release resources */
    fun stop()
    
    /** Seek to a specific position in milliseconds (if supported) */
    fun seekTo(positionMs: Long)
    
    /** Get current playback position in milliseconds */
    val currentPosition: Long
    
    /** Get total duration in milliseconds (0 for live streams) */
    val duration: Long
    
    /** Check if currently playing */
    val isPlaying: Boolean
}

/**
 * Remember a video player controller for programmatic control.
 * 
 * @param config Stream configuration
 * @return A [VideoPlayerController] instance
 */
@Composable
expect fun rememberVideoPlayerController(config: StreamConfig): VideoPlayerController
