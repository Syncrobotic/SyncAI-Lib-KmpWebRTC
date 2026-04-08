package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.syncrobotic.webrtc.session.WebRTCSession

/**
 * Composable that displays the local camera preview from a [WebRTCSession].
 *
 * Requires `mediaConfig.sendVideo = true` in the session's MediaConfig.
 * The session must be connected (or will be auto-connected if `autoConnect = true`)
 * for the camera to start capturing.
 *
 * ```kotlin
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/stream/whip"),
 *     mediaConfig = MediaConfig.SEND_VIDEO
 * )
 * CameraPreview(
 *     session = session,
 *     modifier = Modifier.fillMaxSize(),
 *     mirror = true  // mirror front camera
 * )
 * ```
 *
 * @param session The WebRTC session configured with sendVideo = true
 * @param modifier Compose modifier for sizing and positioning
 * @param mirror Whether to mirror the preview horizontally (typical for front camera)
 * @param onStateChange Optional callback for player state changes
 */
@Composable
expect fun CameraPreview(
    session: WebRTCSession,
    modifier: Modifier = Modifier,
    mirror: Boolean = true,
    onStateChange: ((PlayerState) -> Unit)? = null,
)
