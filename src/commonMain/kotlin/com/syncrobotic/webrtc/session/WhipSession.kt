package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.audio.AudioPushConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages a WebRTC connection for **sending** audio (microphone capture) via WHIP.
 *
 * Handles audio capture, encoding, WHIP signaling, auto-reconnect, and stats collection.
 *
 * ```kotlin
 * val signaling = WhipSignalingAdapter(url = "https://server/stream/whip")
 * val session = WhipSession(signaling)
 * session.connect()    // starts audio capture + streaming
 * session.setMuted(true)
 * session.close()
 * ```
 *
 * @param signaling Signaling adapter for SDP exchange (e.g. [WhipSignalingAdapter])
 * @param audioConfig Audio capture and processing configuration
 * @param retryConfig Retry configuration for auto-reconnect
 */
expect class WhipSession(
    signaling: SignalingAdapter,
    audioConfig: AudioPushConfig = AudioPushConfig(),
    retryConfig: RetryConfig = RetryConfig.DEFAULT
) {
    /** Reactive connection state. */
    val state: StateFlow<SessionState>

    /** Reactive connection statistics (updated every ~1s while connected). */
    val stats: StateFlow<WebRTCStats?>

    /** Establish the WebRTC connection and start audio capture. */
    suspend fun connect()

    /** Create a DataChannel on this connection. Call after [connect]. */
    fun createDataChannel(config: DataChannelConfig): DataChannel?

    /** Mute/unmute the microphone. */
    fun setMuted(muted: Boolean)

    /** Toggle mute state. */
    fun toggleMute()

    /** Close the connection and release all resources (microphone, PeerConnection). */
    fun close()
}
