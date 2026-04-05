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
 * **Deprecated:** Use [WebRTCSession] with [MediaConfig.RECEIVE_VIDEO] instead:
 * ```kotlin
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/stream/whep"),
 *     mediaConfig = MediaConfig.RECEIVE_VIDEO
 * )
 * ```
 *
 * @param signaling Signaling adapter for SDP exchange
 * @param config WebRTC configuration including ICE servers
 * @param retryConfig Retry configuration for auto-reconnect
 */
@Deprecated(
    message = "Use WebRTCSession with MediaConfig.RECEIVE_VIDEO instead. WhepSession will be removed in v3.0.",
    replaceWith = ReplaceWith(
        "WebRTCSession(signaling, MediaConfig.RECEIVE_VIDEO, config, retryConfig)",
        "com.syncrobotic.webrtc.session.WebRTCSession",
        "com.syncrobotic.webrtc.config.MediaConfig"
    )
)
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
