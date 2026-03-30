package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages a WebRTC connection for **receiving** video/audio via WHEP.
 *
 * Handles the full PeerConnection lifecycle: SDP negotiation, ICE gathering,
 * auto-reconnect, and stats collection. Incoming audio is automatically played
 * through the device speaker.
 *
 * ```kotlin
 * val signaling = WhepSignalingAdapter(url = "https://server/stream/whep")
 * val session = WhepSession(signaling)
 * session.connect()
 * // Observe state
 * session.state.collect { state -> ... }
 * // Cleanup
 * session.close()
 * ```
 *
 * @param signaling Signaling adapter for SDP exchange (e.g. [WhepSignalingAdapter])
 * @param config WebRTC configuration including ICE servers
 * @param retryConfig Retry configuration for auto-reconnect
 */
expect class WhepSession(
    signaling: SignalingAdapter,
    config: WebRTCConfig = WebRTCConfig.DEFAULT,
    retryConfig: RetryConfig = RetryConfig.DEFAULT
) {
    /** Reactive connection state. */
    val state: StateFlow<SessionState>

    /** Reactive connection statistics (updated every ~1s while connected). */
    val stats: StateFlow<WebRTCStats?>

    /** Establish the WebRTC connection. Suspends until connected or failed. */
    suspend fun connect()

    /** Create a DataChannel on this connection. Call after [connect]. */
    fun createDataChannel(config: DataChannelConfig): DataChannel?

    /** Enable/disable incoming audio playback. */
    fun setAudioEnabled(enabled: Boolean)

    /** Toggle speakerphone output (Android/iOS only). */
    fun setSpeakerphoneEnabled(enabled: Boolean)

    /** Close the connection and release all resources. */
    fun close()
}
