@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import kotlinx.coroutines.*

/**
 * Android implementation of [AudioPlayer].
 *
 * Auto-connects the session and plays incoming audio through the system speaker.
 * Audio playback is handled automatically by the WebRTC stack — this composable
 * only manages lifecycle and exposes controls.
 *
 * On Android the platform context is injected via [session.setContext] before
 * connecting so that the underlying WebRTC layer can access system audio services.
 */
@Composable
actual fun AudioPlayer(
    session: WebRTCSession,
    autoStart: Boolean,
    onStateChange: ((AudioPlaybackState) -> Unit)?,
): AudioPlayerController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionState by session.state.collectAsState()
    var audioEnabled by remember { mutableStateOf(true) }

    // Auto-connect (set context first so the WebRTC stack can access Android audio)
    LaunchedEffect(session, autoStart) {
        session.setContext(context)
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
