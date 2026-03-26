package com.syncrobotic.webrtc.audio

import com.syncrobotic.webrtc.WebRTCStats

/**
 * Controller interface for managing audio push operations.
 * 
 * This interface provides programmatic control over audio streaming,
 * including start/stop, mute control, and statistics access.
 * 
 * Usage:
 * ```kotlin
 * val controller = rememberAudioPushController(config)
 * 
 * // Start streaming
 * controller.start()
 * 
 * // Toggle mute
 * controller.toggleMute()
 * 
 * // Get statistics
 * val stats = controller.stats
 * println("Bitrate: ${stats?.audioBitrate}")
 * 
 * // Stop streaming
 * controller.stop()
 * ```
 */
interface AudioPushController {
    /** Current state of the audio push connection */
    val state: AudioPushState

    /** Whether audio is currently being streamed */
    val isStreaming: Boolean
        get() = state is AudioPushState.Streaming

    /** Whether audio is muted (connected but not sending) */
    val isMuted: Boolean
        get() = state is AudioPushState.Muted

    /** Whether there is an active connection (streaming or muted) */
    val isConnected: Boolean
        get() = state.isActive

    /** Current WebRTC connection statistics (bitrate, latency, etc.) */
    val stats: WebRTCStats?

    /**
     * Start pushing audio to the WHIP endpoint.
     * 
     * This will:
     * 1. Request microphone permission (platform-specific)
     * 2. Initialize WebRTC connection
     * 3. Create and send SDP offer via WHIP
     * 4. Begin audio streaming
     * 
     * State transitions: Idle -> Connecting -> Streaming
     * On failure: Connecting -> Error (may auto-retry based on config)
     */
    fun start()

    /**
     * Stop pushing audio and disconnect.
     * 
     * This will:
     * 1. Stop audio capture
     * 2. Close WebRTC connection
     * 3. Terminate WHIP session
     * 
     * State transitions: Any -> Disconnected
     */
    fun stop()

    /**
     * Set the mute state without disconnecting.
     * 
     * When muted, the connection remains active but audio is not transmitted.
     * This is useful for temporary silence without reconnection overhead.
     * 
     * @param muted True to mute, false to unmute
     * 
     * State transitions:
     * - Streaming -> Muted (when muted = true)
     * - Muted -> Streaming (when muted = false)
     */
    fun setMuted(muted: Boolean)

    /**
     * Toggle the mute state.
     */
    fun toggleMute() {
        setMuted(!isMuted)
    }

    /**
     * Request updated statistics from the WebRTC connection.
     * 
     * After calling this, the [stats] property will be updated
     * with the latest connection metrics.
     */
    suspend fun refreshStats()
}
