package com.syncrobotic.webrtc.audio

import com.syncrobotic.webrtc.WebRTCStats

/**
 * Non-Composable audio push client for use in any Kotlin context.
 * 
 * This class provides the same functionality as [AudioPushPlayer] but
 * can be used outside of Compose (e.g., in Services, Workers, or plain classes).
 * 
 * Usage:
 * ```kotlin
 * val client = AudioPushClient(config) { state ->
 *     println("State changed: $state")
 * }
 * 
 * // Start streaming
 * client.start()
 * 
 * // Stop when done
 * client.stop()
 * client.release()
 * ```
 * 
 * Platform-specific implementations handle:
 * - Audio capture from microphone
 * - WebRTC peer connection management
 * - WHIP signaling
 * - Automatic reconnection (if configured)
 */
expect class AudioPushClient(
    config: AudioPushConfig,
    onStateChange: OnAudioPushStateChange = {}
) : AudioPushController {
    
    override val state: AudioPushState
    override val stats: WebRTCStats?
    
    override fun start()
    override fun stop()
    override fun setMuted(muted: Boolean)
    override suspend fun refreshStats()
    
    /**
     * Release all resources.
     * 
     * Call this when the client is no longer needed.
     * After calling release(), the client cannot be reused.
     */
    fun release()
}
