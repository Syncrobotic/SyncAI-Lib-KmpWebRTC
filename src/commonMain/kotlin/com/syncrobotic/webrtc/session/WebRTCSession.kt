package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified WebRTC session that supports any combination of sending and receiving media.
 *
 * Media directions are configured via [MediaConfig]:
 * - `receiveVideo` / `receiveAudio` — receive remote media
 * - `sendVideo` / `sendAudio` — send local media (camera/microphone)
 *
 * The signaling adapter determines which server endpoint is used (WHEP, WHIP, or custom).
 * The actual HTTP signaling flow is identical for all — only the URL differs.
 *
 * ## Examples
 *
 * ```kotlin
 * // Receive video + audio (equivalent to legacy WhepSession)
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/stream/whep"),
 *     mediaConfig = MediaConfig.RECEIVE_VIDEO
 * )
 *
 * // Send camera + mic (equivalent to legacy WhipSession + video)
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/stream/whip"),
 *     mediaConfig = MediaConfig.SEND_VIDEO
 * )
 *
 * // Bidirectional audio (intercom)
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/audio"),
 *     mediaConfig = MediaConfig.BIDIRECTIONAL_AUDIO
 * )
 *
 * // Full video call
 * val session = WebRTCSession(
 *     signaling = HttpSignalingAdapter("https://server/room"),
 *     mediaConfig = MediaConfig.VIDEO_CALL
 * )
 * ```
 *
 * @param signaling Signaling adapter for SDP exchange
 * @param mediaConfig Media direction configuration
 * @param webrtcConfig WebRTC configuration (ICE servers, gathering mode, etc.)
 * @param retryConfig Retry/reconnection configuration
 */
expect class WebRTCSession(
    signaling: SignalingAdapter,
    mediaConfig: MediaConfig,
    webrtcConfig: WebRTCConfig = WebRTCConfig.DEFAULT,
    retryConfig: RetryConfig = RetryConfig.DEFAULT
) {
    /** Reactive connection state. */
    val state: StateFlow<SessionState>

    /** Reactive connection statistics (updated every ~1s while connected). */
    val stats: StateFlow<WebRTCStats?>

    /**
     * Establish the WebRTC connection.
     *
     * Initializes media capture (if sending), creates the SDP offer with directions
     * derived from [mediaConfig], exchanges it via [signaling], and starts media flow.
     *
     * Suspends until connected or throws on failure. Auto-reconnect is handled
     * internally based on [retryConfig].
     */
    suspend fun connect()

    /**
     * Create a DataChannel on this connection.
     *
     * For best results, call before [connect] so the channel is included in the initial
     * SDP negotiation. Post-connect creation may require renegotiation.
     */
    fun createDataChannel(config: DataChannelConfig): DataChannel?

    // ── Receive-side controls ─────────────────────────────────────────

    /** Enable/disable incoming audio playback. Only effective when `mediaConfig.receiveAudio = true`. */
    fun setAudioEnabled(enabled: Boolean)

    /** Toggle speakerphone output (mobile platforms only). */
    fun setSpeakerphoneEnabled(enabled: Boolean)

    // ── Send-side controls ────────────────────────────────────────────

    /** Mute/unmute the microphone. Only effective when `mediaConfig.sendAudio = true`. */
    fun setMuted(muted: Boolean)

    /** Toggle microphone mute state. */
    fun toggleMute()

    /** Enable/disable the camera. Only effective when `mediaConfig.sendVideo = true`. */
    fun setVideoEnabled(enabled: Boolean)

    /** Switch between front and rear camera. Only effective when `mediaConfig.sendVideo = true`. */
    fun switchCamera()

    // ── Low-level access for custom implementations ─────────────────

    /**
     * Callback invoked when a remote video frame is received.
     *
     * The `frame` parameter is a platform-specific video frame object:
     * - JVM: `dev.onvoid.webrtc.media.video.VideoFrame`
     * - Android: `org.webrtc.VideoFrame`
     * - iOS: `RTCVideoFrame`
     *
     * Set this **before** calling [connect] to receive all frames.
     * Use this to build a fully custom video renderer without relying on [VideoRenderer].
     *
     * ```kotlin
     * session.onRemoteVideoFrame = { frame ->
     *     // Convert frame to your rendering pipeline
     * }
     * ```
     */
    var onRemoteVideoFrame: ((frame: Any) -> Unit)?

    /**
     * Callback invoked when the local camera video track becomes available.
     *
     * The `track` parameter is a platform-specific video track object:
     * - JVM: `dev.onvoid.webrtc.media.video.VideoTrack`
     * - Android: `org.webrtc.VideoTrack`
     * - iOS: `RTCVideoTrack`
     *
     * Set this **before** calling [connect] to be notified when the camera starts.
     * Use this to build a fully custom camera preview without relying on [CameraPreview].
     *
     * ```kotlin
     * session.onLocalVideoTrack = { track ->
     *     // Add your own sink/renderer to the track
     * }
     * ```
     */
    var onLocalVideoTrack: ((track: Any) -> Unit)?

    // ── Lifecycle ─────────────────────────────────────────────────────

    /** Close the connection and release all resources. */
    fun close()
}
