package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.Composable
import com.syncrobotic.webrtc.session.WebRTCSession

/**
 * Composable for receiving and playing remote audio via a [WebRTCSession].
 *
 * Auto-connects the session (if idle) and exposes playback controls through
 * [AudioPlayerController]. The session must have `mediaConfig.receiveAudio = true`.
 *
 * ```kotlin
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/stream/whep"),
 *     mediaConfig = MediaConfig(receiveAudio = true)
 * )
 * val controller = AudioPlayer(session = session)
 * // controller.setAudioEnabled(false) to mute
 * // controller.setSpeakerphoneEnabled(true) to switch output
 * ```
 *
 * @param session The WebRTC session configured to receive audio
 * @param autoStart If true, automatically connect when composed (default: true)
 * @param onStateChange Optional callback for audio playback state changes
 * @return An [AudioPlayerController] for playback control
 */
@Composable
expect fun AudioPlayer(
    session: WebRTCSession,
    autoStart: Boolean = true,
    onStateChange: ((AudioPlaybackState) -> Unit)? = null,
): AudioPlayerController

/**
 * Playback state for received audio.
 */
sealed class AudioPlaybackState {
    /** Not connected, no audio. */
    data object Idle : AudioPlaybackState()

    /** Connecting to the remote peer. */
    data object Connecting : AudioPlaybackState()

    /** Audio is being received and played. */
    data object Playing : AudioPlaybackState()

    /** Audio is muted (still receiving but not playing). */
    data object Muted : AudioPlaybackState()

    /** Reconnecting after a connection loss. */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int?) : AudioPlaybackState()

    /** An error occurred. */
    data class Error(val message: String, val cause: Throwable? = null) : AudioPlaybackState()

    /** Session closed. */
    data object Disconnected : AudioPlaybackState()
}

/**
 * Controller for managing remote audio playback.
 */
interface AudioPlayerController {
    /** Current playback state. */
    val state: AudioPlaybackState

    /** Whether audio is currently playing. */
    val isPlaying: Boolean

    /** Whether audio output is currently enabled. */
    val isAudioEnabled: Boolean

    /** Enable/disable audio playback (mute incoming audio). */
    fun setAudioEnabled(enabled: Boolean)

    /** Toggle speakerphone output (mobile platforms only). */
    fun setSpeakerphoneEnabled(enabled: Boolean)

    /** Stop playback and close the session. */
    fun stop()
}
