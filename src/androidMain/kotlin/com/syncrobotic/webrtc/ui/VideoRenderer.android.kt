package com.syncrobotic.webrtc.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.syncrobotic.webrtc.config.StreamConfig
import com.syncrobotic.webrtc.config.StreamProtocol
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WhepSession
import org.webrtc.SurfaceViewRenderer

/**
 * Android implementation of session-based VideoRenderer.
 *
 * Auto-connects the [WhepSession] (if idle), creates a [SurfaceViewRenderer]
 * for the session's internal WebRTC client, and maps session state to
 * [PlayerState].
 */
@Composable
actual fun VideoRenderer(
    session: WhepSession,
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

    // Map SessionState → PlayerState + fire onEvent
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
    } else {
        SessionVideoPlaceholder(sessionState, modifier)
    }

    // Cleanup callback on dispose
    DisposableEffect(session) {
        onDispose {
            session.onClientReady = null
        }
    }

    return remember(session) { SessionVideoPlayerController(session) }
}

/**
 * Android implementation of VideoRenderer (legacy config-based API).
 */
@Suppress("DEPRECATION")
@Composable
actual fun VideoRenderer(
    config: StreamConfig,
    modifier: Modifier,
    onStateChange: OnPlayerStateChange,
    onEvent: OnPlayerEvent
) {
    // Use dedicated WebRTC player for WebRTC protocol
    if (config.protocol == StreamProtocol.WEBRTC) {
        WebRTCVideoPlayer(
            config = config,
            modifier = modifier,
            onStateChange = onStateChange,
            onEvent = onEvent
        )
        return
    }
    
    // For HLS/RTSP: show unsupported message
    // ExoPlayer support requires adding Media3 dependencies
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Protocol ${config.protocol.name} not supported on Android without ExoPlayer.\nUse WebRTC protocol instead.",
            color = Color.White
        )
        
        // Report error state
        LaunchedEffect(Unit) {
            onStateChange(PlayerState.Error("${config.protocol.name} not supported on Android. Use WebRTC protocol."))
        }
    }
}

/**
 * Android implementation of VideoPlayerController.
 * For WebRTC streams, control is managed via WebRTCVideoPlayer directly.
 */
private class AndroidVideoPlayerController : VideoPlayerController {
    
    private var _isPlaying = false
    private var _currentPosition = 0L
    private var _duration = 0L
    
    override fun play() {
        _isPlaying = true
    }
    
    override fun pause() {
        _isPlaying = false
    }
    
    override fun stop() {
        _isPlaying = false
    }
    
    override fun seekTo(positionMs: Long) {
        _currentPosition = positionMs
    }
    
    override val currentPosition: Long
        get() = _currentPosition
    
    override val duration: Long
        get() = _duration
    
    override val isPlaying: Boolean
        get() = _isPlaying
}

@Suppress("DEPRECATION")
@Composable
actual fun rememberVideoPlayerController(config: StreamConfig): VideoPlayerController {
    return remember { AndroidVideoPlayerController() }
}
