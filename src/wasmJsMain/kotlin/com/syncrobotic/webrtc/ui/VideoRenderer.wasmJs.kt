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

/**
 * WebAssembly (Browser) implementation of VideoRenderer.
 * 
 * Note: For WasmJS/Browser, use native HTML5 video element with WebRTC MediaStream directly.
 * This Composable is provided for API compatibility.
 * 
 * For full WasmJS WebRTC support, use the WebRTCClient directly and attach
 * the MediaStream to an HTML video element via JS interop.
 */
@Composable
actual fun VideoRenderer(
    config: StreamConfig,
    modifier: Modifier,
    onStateChange: OnPlayerStateChange,
    onEvent: OnPlayerEvent
) {
    // For WasmJS, recommend using native HTML video element
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "For WasmJS/Browser, use native HTML5 video element.\nSee WebRTCClient.getVideoSink() for MediaStream.",
            color = Color.White
        )
        
        LaunchedEffect(Unit) {
            onStateChange(PlayerState.Error("Use native HTML5 video element for WasmJS platform"))
        }
    }
}

/**
 * WasmJS implementation of VideoPlayerController.
 */
private class WasmJsVideoPlayerController : VideoPlayerController {
    
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

@Composable
actual fun rememberVideoPlayerController(config: StreamConfig): VideoPlayerController {
    return remember { WasmJsVideoPlayerController() }
}
