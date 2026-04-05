package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.Composable
import com.syncrobotic.webrtc.session.WebRTCSession

/**
 * JS implementation of [AudioPlayer].
 * Stub — full browser support not yet implemented.
 */
@Composable
actual fun AudioPlayer(
    session: WebRTCSession,
    autoStart: Boolean,
    onStateChange: ((AudioPlaybackState) -> Unit)?,
): AudioPlayerController {
    TODO("AudioPlayer not yet implemented for JS platform")
}
