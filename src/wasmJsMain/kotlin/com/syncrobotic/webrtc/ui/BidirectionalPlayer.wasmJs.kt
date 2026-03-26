package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.syncrobotic.webrtc.audio.*
import com.syncrobotic.webrtc.config.BidirectionalConfig

/**
 * WebAssembly/Browser implementation of BidirectionalPlayer.
 */
@Composable
actual fun BidirectionalPlayer(
    config: BidirectionalConfig,
    modifier: Modifier,
    onStateChange: OnBidirectionalStateChange,
    onVideoEvent: OnPlayerEvent
): BidirectionalController {
    var videoState by remember { mutableStateOf<PlayerState>(PlayerState.Idle) }
    var audioState by remember { mutableStateOf<AudioPushState>(AudioPushState.Idle) }
    
    // Report combined state changes
    LaunchedEffect(videoState, audioState) {
        onStateChange(BidirectionalState(videoState, audioState))
    }
    
    // Video controller (optional)
    val videoController: VideoPlayerController? = if (config.hasVideoReceive) {
        rememberVideoPlayerController(config.videoConfig)
    } else {
        null
    }
    
    // Audio controller (optional)
    val audioController: AudioPushController? = if (config.hasAudioSend && config.audioConfig != null) {
        AudioPushPlayer(
            config = config.audioConfig,
            autoStart = config.autoStartAudio,
            onStateChange = { audioState = it }
        )
    } else {
        null
    }
    
    // Video renderer (only if video is enabled)
    if (config.hasVideoReceive) {
        Box(modifier = modifier) {
            VideoRenderer(
                config = config.videoConfig,
                modifier = Modifier.matchParentSize(),
                onStateChange = { videoState = it },
                onEvent = onVideoEvent
            )
        }
    }
    
    // Auto-start video if configured
    LaunchedEffect(config.autoStartVideo) {
        if (config.autoStartVideo && config.hasVideoReceive) {
            videoController?.play()
        }
    }
    
    // Create controller
    return remember(videoController, audioController) {
        BidirectionalControllerImpl(
            videoController = videoController,
            audioController = audioController,
            getVideoState = { videoState },
            getAudioState = { audioState }
        )
    }
}

/**
 * Remember a BidirectionalController with automatic lifecycle management.
 */
@Composable
actual fun rememberBidirectionalController(
    config: BidirectionalConfig,
    onStateChange: OnBidirectionalStateChange
): BidirectionalController {
    return BidirectionalPlayer(
        config = config,
        modifier = Modifier,
        onStateChange = onStateChange,
        onVideoEvent = {}
    )
}

/**
 * Internal implementation of BidirectionalController.
 */
internal class BidirectionalControllerImpl(
    override val videoController: VideoPlayerController?,
    override val audioController: AudioPushController?,
    private val getVideoState: () -> PlayerState,
    private val getAudioState: () -> AudioPushState
) : BidirectionalController {
    
    override val state: BidirectionalState
        get() = BidirectionalState(getVideoState(), getAudioState())
    
    override fun startVideo() {
        videoController?.play()
    }
    
    override fun stopVideo() {
        videoController?.stop()
    }
    
    override fun startAudio() {
        audioController?.start()
    }
    
    override fun stopAudio() {
        audioController?.stop()
    }
    
    override fun setAudioMuted(muted: Boolean) {
        audioController?.setMuted(muted)
    }
    
    override fun toggleAudioMute() {
        audioController?.toggleMute()
    }
    
    override fun startAll() {
        startVideo()
        startAudio()
    }
    
    override fun stopAll() {
        stopVideo()
        stopAudio()
    }
}
