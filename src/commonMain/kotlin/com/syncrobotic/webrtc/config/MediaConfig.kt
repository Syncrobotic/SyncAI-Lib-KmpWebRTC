package com.syncrobotic.webrtc.config

import com.syncrobotic.webrtc.audio.AudioPushConfig

/**
 * Configuration for media directions in a [WebRTCSession][com.syncrobotic.webrtc.session.WebRTCSession].
 *
 * Controls which media types (video/audio) are sent and/or received. The combination of
 * flags determines the transceiver direction in the SDP offer:
 * - send + receive → `sendrecv`
 * - send only → `sendonly`
 * - receive only → `recvonly`
 * - neither → transceiver not created
 *
 * ```kotlin
 * // Receive video + bidirectional audio (intercom scenario)
 * val config = MediaConfig(
 *     receiveVideo = true,
 *     receiveAudio = true,
 *     sendAudio = true
 * )
 * ```
 *
 * @param receiveVideo Whether to receive remote video
 * @param receiveAudio Whether to receive remote audio
 * @param sendVideo Whether to send local camera video
 * @param sendAudio Whether to send local microphone audio
 * @param audioConfig Audio capture settings (echo cancellation, noise suppression, etc.)
 * @param videoConfig Camera capture settings (resolution, fps, front/rear camera)
 */
data class MediaConfig(
    val receiveVideo: Boolean = false,
    val receiveAudio: Boolean = false,
    val sendVideo: Boolean = false,
    val sendAudio: Boolean = false,
    val audioConfig: AudioPushConfig = AudioPushConfig(),
    val videoConfig: VideoCaptureConfig = VideoCaptureConfig.HD
) {
    /**
     * Transceiver direction for video, or `null` if video is not needed.
     */
    val videoDirection: TransceiverDirection?
        get() = directionFor(sendVideo, receiveVideo)

    /**
     * Transceiver direction for audio, or `null` if audio is not needed.
     */
    val audioDirection: TransceiverDirection?
        get() = directionFor(sendAudio, receiveAudio)

    /**
     * Whether any media requires sending (needs mic/camera permissions).
     */
    val requiresSending: Boolean get() = sendVideo || sendAudio

    /**
     * Whether any media requires receiving.
     */
    val requiresReceiving: Boolean get() = receiveVideo || receiveAudio

    companion object {
        /**
         * Receive video + audio (equivalent to legacy WhepSession default).
         * Typical use: watching a stream.
         */
        val RECEIVE_VIDEO = MediaConfig(receiveVideo = true, receiveAudio = true)

        /**
         * Send audio only (equivalent to legacy WhipSession default).
         * Typical use: push microphone to server.
         */
        val SEND_AUDIO = MediaConfig(sendAudio = true)

        /**
         * Send video + audio.
         * Typical use: camera live streaming / broadcasting.
         */
        val SEND_VIDEO = MediaConfig(sendVideo = true, sendAudio = true)

        /**
         * Bidirectional audio (sendrecv).
         * Typical use: voice intercom / walkie-talkie.
         */
        val BIDIRECTIONAL_AUDIO = MediaConfig(receiveAudio = true, sendAudio = true)

        /**
         * Full video call: send and receive both video and audio.
         */
        val VIDEO_CALL = MediaConfig(
            receiveVideo = true,
            receiveAudio = true,
            sendVideo = true,
            sendAudio = true
        )

        private fun directionFor(send: Boolean, receive: Boolean): TransceiverDirection? = when {
            send && receive -> TransceiverDirection.SEND_RECV
            send -> TransceiverDirection.SEND_ONLY
            receive -> TransceiverDirection.RECV_ONLY
            else -> null
        }
    }
}
