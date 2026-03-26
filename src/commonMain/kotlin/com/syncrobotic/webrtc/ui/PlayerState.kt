package com.syncrobotic.webrtc.ui

/**
 * Represents the current state of a video player.
 */
sealed interface PlayerState {
    /** Player is idle, not connected */
    data object Idle : PlayerState
    
    /** Player is connecting to the stream */
    data object Connecting : PlayerState
    
    /** Player is initializing or buffering for the first time */
    data object Loading : PlayerState
    
    /** Player is actively playing content */
    data object Playing : PlayerState
    
    /** Player is paused */
    data object Paused : PlayerState
    
    /** Player is buffering during playback */
    data class Buffering(val percent: Int = 0) : PlayerState
    
    /** Player has stopped (end of stream or manually stopped) */
    data object Stopped : PlayerState
    
    /** Player encountered an error */
    data class Error(val message: String, val cause: Throwable? = null) : PlayerState
    
    /** Player is attempting to reconnect after a failure or disconnection */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val reason: String = "",
        val nextRetryMs: Long = 0
    ) : PlayerState
    
    /** Display name for UI */
    val displayName: String
        get() = when (this) {
            is Idle -> "Idle"
            is Connecting -> "Connecting..."
            is Loading -> "Loading..."
            is Playing -> "Playing"
            is Paused -> "Paused"
            is Buffering -> "Buffering ${percent}%"
            is Stopped -> "Stopped"
            is Error -> "Error"
            is Reconnecting -> "Reconnecting ($attempt/$maxAttempts)..."
        }
}

/**
 * Stream information received from the source.
 */
data class StreamInfo(
    val width: Int = 0,
    val height: Int = 0,
    val codec: String = "Unknown",
    val fps: Float = 0f,
    val bitrate: Long? = null,
    val protocol: String = "Unknown",
    val videoTrackCount: Int = 0,
    val audioTrackCount: Int = 0
) {
    val resolution: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else "Unknown"
    
    val fpsDisplay: String
        get() = if (fps > 0) "${(fps * 10).toInt() / 10.0} fps" else "Unknown"
    
    val bitrateDisplay: String
        get() = bitrate?.let { 
            when {
                it > 1_000_000 -> "${(it / 100_000) / 10.0} Mbps"
                it > 1_000 -> "${it / 1_000} Kbps"
                else -> "$it bps"
            }
        } ?: "Unknown"
}

/**
 * Player events that don't change the state but provide information.
 */
sealed interface PlayerEvent {
    /** First video frame has been rendered */
    data class FirstFrameRendered(val timestampMs: Long) : PlayerEvent
    
    /** Stream information received */
    data class StreamInfoReceived(val info: StreamInfo) : PlayerEvent
    
    /** Bitrate changed during playback */
    data class BitrateChanged(val bitrate: Long) : PlayerEvent
    
    /** Frame received (for statistics) */
    data class FrameReceived(val timestampMs: Long) : PlayerEvent
}

/**
 * Callback interface for player state changes.
 */
typealias OnPlayerStateChange = (PlayerState) -> Unit

/**
 * Callback interface for player events.
 */
typealias OnPlayerEvent = (PlayerEvent) -> Unit
