package com.syncrobotic.webrtc.audio

import androidx.compose.runtime.*
import com.syncrobotic.webrtc.session.WebRTCSession

/**
 * WasmJS implementation of session-based AudioPushPlayer for [WebRTCSession].
 * Stub -- full browser support not yet implemented.
 */
@Composable
actual fun AudioPushPlayer(
    session: WebRTCSession,
    autoStart: Boolean,
    onStateChange: ((AudioPushState) -> Unit)?,
): AudioPushController {
    TODO("WebRTCSession AudioPushPlayer not yet implemented for this platform")
}
