package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.*
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import kotlinx.coroutines.*

/**
 * iOS implementation of session-based AudioPushPlayer for [WebRTCSession].
 */
@Composable
actual fun AudioPushPlayer(
    session: WebRTCSession,
    autoStart: Boolean,
    onStateChange: ((AudioPushState) -> Unit)?,
): AudioPushController {
    val scope = rememberCoroutineScope()
    val sessionState by session.state.collectAsState()

    LaunchedEffect(session, autoStart) {
        if (autoStart && (session.state.value == SessionState.Idle || session.state.value is SessionState.Error)) {
            session.connect()
        }
    }

    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toAudioPushState())
    }

    DisposableEffect(session) {
        onDispose { /* Session lifecycle managed by user */ }
    }

    return remember(session) { WebRTCSessionAudioPushController(session, scope) }
}
