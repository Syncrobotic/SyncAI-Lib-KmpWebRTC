package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import cocoapods.GoogleWebRTC.RTCMTLVideoView
import kotlinx.cinterop.*
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of session-based VideoRenderer for [WebRTCSession].
 *
 * Auto-connects the [WebRTCSession], creates an [RTCMTLVideoView] via the
 * session's internal WebRTC client, and maps session state to [PlayerState].
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoRenderer(
    session: WebRTCSession,
    modifier: Modifier,
    onStateChange: ((PlayerState) -> Unit)?,
    onEvent: ((PlayerEvent) -> Unit)?,
): VideoPlayerController {
    var videoView by remember { mutableStateOf<RTCMTLVideoView?>(null) }
    val sessionState by session.state.collectAsState()
    val connectionStartTime = remember { (NSDate().timeIntervalSince1970 * 1000).toLong() }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }

    // Set up video rendering callback and auto-connect
    LaunchedEffect(session) {
        session.onClientReady = { client ->
            // Reset state for new connection (reconnect scenario)
            hasReportedFirstFrame = false
            videoView = client.createVideoView()
        }
        if (session.state.value == SessionState.Idle || session.state.value is SessionState.Error) {
            session.connect()
        }
    }

    // Map SessionState -> PlayerState + fire onEvent
    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toPlayerState())
        if (sessionState == SessionState.Connected && !hasReportedFirstFrame) {
            hasReportedFirstFrame = true
            val elapsed = (NSDate().timeIntervalSince1970 * 1000).toLong() - connectionStartTime
            onEvent?.invoke(PlayerEvent.FirstFrameRendered(elapsed))
        }
    }

    // Render video or placeholder
    val view = videoView
    if (view != null) {
        key(view) {
            UIKitView(
                factory = { view },
                modifier = modifier,
            )
        }
    } else {
        SessionVideoPlaceholder(sessionState, modifier)
    }

    // Cleanup
    DisposableEffect(session) {
        onDispose {
            session.onClientReady = null
        }
    }

    return remember(session) { WebRTCSessionVideoPlayerController(session) }
}
