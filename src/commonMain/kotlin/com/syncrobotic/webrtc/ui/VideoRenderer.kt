package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.syncrobotic.webrtc.session.WebRTCSession

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
 * Interface for controlling a video player programmatically.
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
