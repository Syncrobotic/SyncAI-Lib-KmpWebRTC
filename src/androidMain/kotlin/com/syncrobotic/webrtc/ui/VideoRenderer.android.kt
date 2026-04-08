package com.syncrobotic.webrtc.ui

import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import org.webrtc.SurfaceViewRenderer

/**
 * Android implementation of session-based VideoRenderer for [WebRTCSession].
 *
 * Auto-connects the [WebRTCSession] (if idle), creates a [SurfaceViewRenderer]
 * for the session's internal WebRTC client, and maps session state to
 * [PlayerState].
 */
@Composable
actual fun VideoRenderer(
    session: WebRTCSession,
    modifier: Modifier,
    onStateChange: ((PlayerState) -> Unit)?,
    onEvent: ((PlayerEvent) -> Unit)?,
): VideoPlayerController {
    val context = LocalContext.current
    var surfaceViewRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val sessionState by session.state.collectAsState()
    val connectionStartTime = remember { System.currentTimeMillis() }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }

    // Set up video rendering callback and auto-connect
    LaunchedEffect(session) {
        session.onClientReady = { client, ctx ->
            surfaceViewRenderer = client.createSurfaceViewRenderer(ctx)
        }
        session.setContext(context)
        if (session.state.value == SessionState.Idle || session.state.value is SessionState.Error) {
            session.connect()
        }
    }

    // Map SessionState -> PlayerState + fire onEvent
    LaunchedEffect(sessionState) {
        val playerState = sessionState.toPlayerState()
        onStateChange?.invoke(playerState)
        if (sessionState == SessionState.Connected && !hasReportedFirstFrame) {
            hasReportedFirstFrame = true
            val elapsed = System.currentTimeMillis() - connectionStartTime
            onEvent?.invoke(PlayerEvent.FirstFrameRendered(elapsed))
        }
    }

    // Render video or placeholder
    val renderer = surfaceViewRenderer
    if (renderer != null) {
        key(renderer) {
            AndroidView(
                factory = {
                    renderer.apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = modifier
            )
        }
    } else {
        SessionVideoPlaceholder(sessionState, modifier)
    }

    // Cleanup callback on dispose
    DisposableEffect(session) {
        onDispose {
            session.onClientReady = null
        }
    }

    return remember(session) { WebRTCSessionVideoPlayerController(session) }
}
