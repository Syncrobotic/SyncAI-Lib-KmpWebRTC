@file:Suppress("DEPRECATION")

package com.syncrobotic.webrtc.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cocoapods.GoogleWebRTC.RTCMTLVideoView
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewContentMode

/**
 * iOS implementation of [CameraPreview].
 *
 * Displays the local camera feed from a [WebRTCSession] configured with
 * `sendVideo = true`. Creates an [RTCMTLVideoView], adds it as a renderer
 * to the client's local video track, and renders via [UIKitView].
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun CameraPreview(
    session: WebRTCSession,
    modifier: Modifier,
    mirror: Boolean,
    onStateChange: ((PlayerState) -> Unit)?,
) {
    var videoView by remember { mutableStateOf<RTCMTLVideoView?>(null) }
    val sessionState by session.state.collectAsState()

    // Set up local video rendering callback and auto-connect
    LaunchedEffect(session) {
        session.onClientReady = { client ->
            val view = RTCMTLVideoView()
            view.videoContentMode = if (mirror) {
                UIViewContentMode.UIViewContentModeScaleAspectFill
            } else {
                UIViewContentMode.UIViewContentModeScaleAspectFit
            }
            // Mirror the view transform for front camera preview
            if (mirror) {
                view.transform = platform.CoreGraphics.CGAffineTransformMakeScale(-1.0, 1.0)
            }
            // Add view as renderer to local video track for camera preview
            client.getLocalVideoTrack()?.addRenderer(view)
            videoView = view
        }
        if (session.state.value == SessionState.Idle || session.state.value is SessionState.Error) {
            session.connect()
        }
    }

    // Map SessionState -> PlayerState
    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toPlayerState())
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

    // Cleanup on dispose
    DisposableEffect(session) {
        onDispose {
            session.onClientReady = null
        }
    }
}
