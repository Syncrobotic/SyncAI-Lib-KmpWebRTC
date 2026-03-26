package com.syncrobotic.webrtc.audio

/**
 * Represents the state of an audio push connection.
 */
sealed class AudioPushState {
    /** Initial state, not connected */
    data object Idle : AudioPushState()
    
    /** Connecting to the WHIP endpoint */
    data object Connecting : AudioPushState()
    
    /** Connected and streaming audio */
    data object Streaming : AudioPushState()
    
    /** Audio is muted but still connected */
    data object Muted : AudioPushState()
    
    /** Attempting to reconnect after connection loss */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int
    ) : AudioPushState()
    
    /** Connection failed */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRetryable: Boolean = true
    ) : AudioPushState()
    
    /** Disconnected (intentionally or after max retries) */
    data object Disconnected : AudioPushState()
    
    /**
     * Check if the state represents an active connection.
     */
    val isActive: Boolean
        get() = this is Streaming || this is Muted
    
    /**
     * Check if the state represents a terminal state.
     */
    val isTerminal: Boolean
        get() = this is Disconnected || (this is Error && !this.isRetryable)
}

/**
 * Callback for audio push state changes.
 */
typealias OnAudioPushStateChange = (AudioPushState) -> Unit
