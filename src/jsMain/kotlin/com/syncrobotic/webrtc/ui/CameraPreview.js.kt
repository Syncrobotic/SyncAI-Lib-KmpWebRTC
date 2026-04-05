package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.syncrobotic.webrtc.session.WebRTCSession

/**
 * JS stub implementation of [CameraPreview].
 * Not yet implemented for browser targets.
 */
@Composable
actual fun CameraPreview(
    session: WebRTCSession,
    modifier: Modifier,
    mirror: Boolean,
    onStateChange: ((PlayerState) -> Unit)?,
) {
    TODO("CameraPreview not yet implemented for this platform")
}
