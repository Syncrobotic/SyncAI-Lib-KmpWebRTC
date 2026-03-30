package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.syncrobotic.webrtc.config.StreamConfig
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WhepSession

/**
 * JS implementation of session-based VideoRenderer.
 * Stub — full browser support not yet implemented.
 */
@Composable
actual fun VideoRenderer(
    session: WhepSession,
    modifier: Modifier,
    onStateChange: ((PlayerState) -> Unit)?,
    onEvent: ((PlayerEvent) -> Unit)?,
): VideoPlayerController {
    val sessionState by session.state.collectAsState()
    val connectionStartTime = remember { kotlin.js.Date.now().toLong() }
    var hasReportedFirstFrame by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        if (session.state.value == SessionState.Idle) {
            session.connect()
        }
    }

    LaunchedEffect(sessionState) {
        onStateChange?.invoke(sessionState.toPlayerState())
        if (sessionState == SessionState.Connected && !hasReportedFirstFrame) {
            hasReportedFirstFrame = true
            val elapsed = kotlin.js.Date.now().toLong() - connectionStartTime
            onEvent?.invoke(PlayerEvent.FirstFrameRendered(elapsed))
        }
    }

    SessionVideoPlaceholder(sessionState, modifier)

    return remember { JsVideoPlayerController() }
}

/**
 * JS implementation of VideoRenderer (legacy config-based API).
 */
@Suppress("DEPRECATION")
@Composable
actual fun VideoRenderer(
    config: StreamConfig,
    modifier: Modifier,
    onStateChange: OnPlayerStateChange,
    onEvent: OnPlayerEvent
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "For JS/Browser, use native HTML5 video element.\nSee WebRTCClient.getVideoSink() for MediaStream.",
            color = Color.White
        )
        
        LaunchedEffect(Unit) {
            onStateChange(PlayerState.Error("Use native HTML5 video element for JS platform"))
        }
    }
}

/**
 * JS implementation of VideoPlayerController.
 */
private class JsVideoPlayerController : VideoPlayerController {
    
    private var _isPlaying = false
    
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
        // Not supported for live streams
    }
    
    override val currentPosition: Long
        get() = 0L
    
    override val duration: Long
        get() = 0L
    
    override val isPlaying: Boolean
        get() = _isPlaying
}

@Suppress("DEPRECATION")
@Composable
actual fun rememberVideoPlayerController(config: StreamConfig): VideoPlayerController {
    return remember { JsVideoPlayerController() }
}
