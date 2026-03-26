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
import com.syncrobotic.webrtc.config.StreamProtocol

/**
 * Android implementation of VideoRenderer.
 * 
 * Currently supports:
 * - WebRTC streams via WebRTC-SDK Android (routes to WebRTCVideoPlayer)
 * 
 * Note: HLS/RTSP support requires ExoPlayer (Media3) which is not currently
 * bundled in the SDK. For non-WebRTC streams, consider using the WebRTC
 * fallback or adding ExoPlayer dependencies to your project.
 */
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

@Composable
actual fun rememberVideoPlayerController(config: StreamConfig): VideoPlayerController {
    return remember { AndroidVideoPlayerController() }
}
