package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.syncrobotic.webrtc.config.StreamConfig
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.session.WhepSession

/**
 * A cross-platform video renderer driven by a [WhepSession].
 *
 * The composable auto-connects the session (if idle), renders the incoming
 * video, and maps [com.syncrobotic.webrtc.session.SessionState] to
 * [PlayerState] for the caller.
 *
 * ```kotlin
 * val signaling = WhepSignalingAdapter(url = "https://server/stream/whep")
 * val session = WhepSession(signaling)
 *
 * val controller = VideoRenderer(
 *     session  = session,
 *     modifier = Modifier.fillMaxSize(),
 *     onStateChange = { state -> println(state) }
 * )
 * ```
 *
 * @param session  The WHEP session that manages connection and media
 * @param modifier Compose modifier for sizing and positioning
 * @param onStateChange Optional callback for player state changes
 * @param onEvent Optional callback for player events (info, stats)
 * @return A [VideoPlayerController] for programmatic playback control
 */
@Composable
expect fun VideoRenderer(
    session: WhepSession,
    modifier: Modifier = Modifier,
    onStateChange: ((PlayerState) -> Unit)? = null,
    onEvent: ((PlayerEvent) -> Unit)? = null,
): VideoPlayerController

/**
 * A cross-platform video renderer driven by a [WebRTCSession].
 *
 * Requires `mediaConfig.receiveVideo = true` in the session's [MediaConfig].
 *
 * ```kotlin
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/stream/whep"),
 *     mediaConfig = MediaConfig.RECEIVE_VIDEO
 * )
 * VideoRenderer(session = session, modifier = Modifier.fillMaxSize())
 * ```
 */
@Composable
expect fun VideoRenderer(
    session: WebRTCSession,
    modifier: Modifier = Modifier,
    onStateChange: ((PlayerState) -> Unit)? = null,
    onEvent: ((PlayerEvent) -> Unit)? = null,
): VideoPlayerController

/**
 * A cross-platform video renderer component (legacy config-based API).
 *
 * @param config Stream configuration including URL, protocol, and display options
 * @param modifier Compose modifier for sizing and positioning
 * @param onStateChange Callback for player state changes
 * @param onEvent Callback for player events (info, stats)
 */
@Deprecated(
    message = "Use VideoRenderer(session: WhepSession, ...) instead. Will be removed in v3.0.",
    replaceWith = ReplaceWith("VideoRenderer(session, modifier, onStateChange, onEvent)")
)
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
@Deprecated(
    message = "Use VideoRenderer(session: WhepSession, ...) which returns a VideoPlayerController directly. Will be removed in v3.0.",
    replaceWith = ReplaceWith("VideoRenderer(session)")
)
@Composable
expect fun rememberVideoPlayerController(config: StreamConfig): VideoPlayerController
