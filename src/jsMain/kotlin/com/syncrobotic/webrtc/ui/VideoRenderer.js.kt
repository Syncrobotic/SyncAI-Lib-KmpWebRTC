package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.syncrobotic.webrtc.session.WebRTCSession

/**
 * JS implementation of session-based VideoRenderer for [WebRTCSession].
 * Stub -- full browser support not yet implemented.
 */
@Composable
actual fun VideoRenderer(
    session: WebRTCSession,
    modifier: Modifier,
    onStateChange: ((PlayerState) -> Unit)?,
    onEvent: ((PlayerEvent) -> Unit)?,
): VideoPlayerController {
    TODO("WebRTCSession VideoRenderer not yet implemented for this platform")
}
