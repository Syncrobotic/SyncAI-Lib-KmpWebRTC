package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.syncrobotic.webrtc.audio.*
import com.syncrobotic.webrtc.config.BidirectionalConfig

/**
 * Combined state for bidirectional streaming.
 */
data class BidirectionalState(
    val videoState: PlayerState = PlayerState.Idle,
    val audioState: AudioPushState = AudioPushState.Idle
) {
    /**
     * Whether both video and audio are active/connected.
     */
    val isFullyConnected: Boolean
        get() = videoState is PlayerState.Playing && audioState is AudioPushState.Streaming
    
    /**
     * Whether any component has an error.
     */
    val hasError: Boolean
        get() = videoState is PlayerState.Error || audioState is AudioPushState.Error
    
    /**
     * Combined error message if any.
     */
    val errorMessage: String?
        get() = when {
            videoState is PlayerState.Error -> "Video: ${videoState.message}"
            audioState is AudioPushState.Error -> "Audio: ${audioState.message}"
            else -> null
        }
}

/**
 * Controller for managing bidirectional streaming.
 */
interface BidirectionalController {
    /** Current combined state */
    val state: BidirectionalState
    
    /** Video player controller (null if video disabled) */
    val videoController: VideoPlayerController?
    
    /** Audio push controller (null if audio disabled) */
    val audioController: AudioPushController?
    
    /** Start video playback */
    fun startVideo()
    
    /** Stop video playback */
    fun stopVideo()
    
    /** Start audio sending */
    fun startAudio()
    
    /** Stop audio sending */
    fun stopAudio()
    
    /** Mute/unmute outgoing audio */
    fun setAudioMuted(muted: Boolean)
    
    /** Toggle audio mute state */
    fun toggleAudioMute()
    
    /** Start both video and audio */
    fun startAll()
    
    /** Stop both video and audio */
    fun stopAll()
}

/**
 * Callback for bidirectional state changes.
 */
typealias OnBidirectionalStateChange = (BidirectionalState) -> Unit

/**
 * A composable that combines video receiving (WHEP) with audio sending (WHIP)
 * for bidirectional WebRTC communication.
 * 
 * This is a high-level component that simplifies setting up bidirectional
 * communication with a single configuration.
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun RobotControlScreen() {
 *     val config = BidirectionalConfig.create(
 *         host = "192.168.1.100",
 *         receiveStreamPath = "robot-video",
 *         sendStreamPath = "mobile-audio"
 *     )
 *     
 *     var state by remember { mutableStateOf(BidirectionalState()) }
 *     
 *     val controller = BidirectionalPlayer(
 *         config = config,
 *         modifier = Modifier.fillMaxSize(),
 *         onStateChange = { state = it }
 *     )
 *     
 *     // Control buttons
 *     Row {
 *         Button(onClick = { controller.startAudio() }) {
 *             Text("Push to Talk")
 *         }
 *         Button(onClick = { controller.toggleAudioMute() }) {
 *             Text(if (controller.audioController?.isMuted == true) "Unmute" else "Mute")
 *         }
 *     }
 * }
 * ```
 * 
 * @param config Bidirectional configuration
 * @param modifier Compose modifier for the video view
 * @param onStateChange Callback for combined state changes
 * @param onVideoEvent Callback for video player events
 * @return A BidirectionalController for managing the streams
 */
@Deprecated(
    message = "Use VideoRenderer(session: WhepSession) + AudioPushPlayer(session: WhipSession) separately. Will be removed in v3.0.",
    replaceWith = ReplaceWith("VideoRenderer(whepSession) and AudioPushPlayer(whipSession)")
)
@Composable
expect fun BidirectionalPlayer(
    config: BidirectionalConfig,
    modifier: Modifier = Modifier,
    onStateChange: OnBidirectionalStateChange = {},
    onVideoEvent: OnPlayerEvent = {}
): BidirectionalController

/**
 * Remember a BidirectionalController with automatic lifecycle management.
 * 
 * @param config Bidirectional configuration
 * @param onStateChange Callback for state changes
 * @return A BidirectionalController
 */
@Deprecated(
    message = "Use VideoRenderer(session: WhepSession) + AudioPushPlayer(session: WhipSession) separately. Will be removed in v3.0.",
    replaceWith = ReplaceWith("VideoRenderer(whepSession) and AudioPushPlayer(whipSession)")
)
@Composable
expect fun rememberBidirectionalController(
    config: BidirectionalConfig,
    onStateChange: OnBidirectionalStateChange = {}
): BidirectionalController
