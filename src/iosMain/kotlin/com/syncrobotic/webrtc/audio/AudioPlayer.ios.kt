@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.*
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import kotlinx.coroutines.*

/**
 * iOS implementation of [AudioPlayer].
 *
 * Auto-connects the session and plays incoming audio through the system speaker.
 * Audio playback is handled automatically by the WebRTC stack — this composable
 * only manages lifecycle and exposes controls.
 */
@Composable
actual fun AudioPlayer(
    session: WebRTCSession,
    autoStart: Boolean,
    onStateChange: ((AudioPlaybackState) -> Unit)?,
): AudioPlayerController {
    val scope = rememberCoroutineScope()
    val sessionState by session.state.collectAsState()
    var audioEnabled by remember { mutableStateOf(true) }

    // Auto-connect
    LaunchedEffect(session, autoStart) {
        if (autoStart && (session.state.value == SessionState.Idle || session.state.value is SessionState.Error)) {
            session.connect()
        }
    }

    // Map state changes
    LaunchedEffect(sessionState, audioEnabled) {
        onStateChange?.invoke(sessionState.toAudioPlaybackState(audioEnabled))
    }

    DisposableEffect(session) {
        onDispose { /* Session lifecycle managed by user */ }
    }

    return remember(session) {
        object : AudioPlayerController {
            override val state: AudioPlaybackState
                get() = session.state.value.toAudioPlaybackState(audioEnabled)

            override val isPlaying: Boolean
                get() = session.state.value == SessionState.Connected && audioEnabled

            override val isAudioEnabled: Boolean
                get() = audioEnabled

            override fun setAudioEnabled(enabled: Boolean) {
                audioEnabled = enabled
                session.setAudioEnabled(enabled)
            }

            override fun setSpeakerphoneEnabled(enabled: Boolean) {
                session.setSpeakerphoneEnabled(enabled)
            }

            override fun stop() {
                session.close()
            }
        }
    }
}
