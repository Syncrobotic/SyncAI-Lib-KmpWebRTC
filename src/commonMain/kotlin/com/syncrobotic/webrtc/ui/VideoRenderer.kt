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
 *
 * The built-in status/error overlay shows user-facing copy derived from
 * [PlayerState.Error.kind] and offers a retry when the failure is recoverable.
 * Supply [errorContent] to replace it with your own — for localized strings, app
 * typography, or a re-authentication flow:
 *
 * ```kotlin
 * VideoRenderer(
 *     session = session,
 *     errorContent = { error, retry ->
 *         when (error.kind) {
 *             SessionErrorKind.UNAUTHORIZED -> ReAuthPrompt(onDone = retry)
 *             else -> MyErrorCard(text = localized(error.kind), onRetry = retry)
 *         }
 *     }
 * )
 * ```
 *
 * @param errorContent Optional replacement for the built-in error UI. Receives the
 *   [PlayerState.Error] and a `retry` lambda that re-establishes the connection.
 *   When `null`, the built-in overlay is used.
 */
@Composable
expect fun VideoRenderer(
    session: WebRTCSession,
    modifier: Modifier = Modifier,
    onStateChange: ((PlayerState) -> Unit)? = null,
    onEvent: ((PlayerEvent) -> Unit)? = null,
    errorContent: (@Composable (error: PlayerState.Error, retry: () -> Unit) -> Unit)? = null,
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
