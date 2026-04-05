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
 * **Deprecated:** Use [WebRTCSession] with [MediaConfig.SEND_AUDIO] instead:
 * ```kotlin
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/stream/whip"),
 *     mediaConfig = MediaConfig.SEND_AUDIO
 * )
 * ```
 *
 * @param signaling Signaling adapter for SDP exchange
 * @param audioConfig Audio capture and processing configuration
 * @param retryConfig Retry configuration for auto-reconnect
 */
@Deprecated(
    message = "Use WebRTCSession with MediaConfig.SEND_AUDIO instead. WhipSession will be removed in v3.0.",
    replaceWith = ReplaceWith(
        "WebRTCSession(signaling, MediaConfig.SEND_AUDIO)",
        "com.syncrobotic.webrtc.session.WebRTCSession",
        "com.syncrobotic.webrtc.config.MediaConfig"
    )
)
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
