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
 * Android implementation of [CameraPreview].
 *
 * Displays the local camera feed from a [WebRTCSession] configured with
 * `sendVideo = true`. Creates a [SurfaceViewRenderer], adds it as a sink
 * to the client's local video track, and renders via [AndroidView].
 */
@Composable
actual fun CameraPreview(
    session: WebRTCSession,
    modifier: Modifier,
    mirror: Boolean,
    onStateChange: ((PlayerState) -> Unit)?,
) {
    val context = LocalContext.current
    var surfaceViewRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    val sessionState by session.state.collectAsState()

    // Set up local video rendering callback and auto-connect
    LaunchedEffect(session) {
        session.onClientReady = { client, ctx ->
            val renderer = client.createSurfaceViewRenderer(ctx)
            renderer.setMirror(mirror)
            // Add renderer as sink to local video track for camera preview
            client.getLocalVideoTrack()?.addSink(renderer)
            surfaceViewRenderer = renderer
        }
        session.setContext(context)
        if (session.state.value == SessionState.Idle || session.state.value is SessionState.Error) {
            session.connect()
        }
    }

    // Update mirror when it changes
    LaunchedEffect(mirror) {
        surfaceViewRenderer?.setMirror(mirror)
    }

    // Map SessionState -> PlayerState
    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toPlayerState())
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

    // Cleanup on dispose
    DisposableEffect(session) {
        onDispose {
            session.onClientReady = null
        }
    }
}
