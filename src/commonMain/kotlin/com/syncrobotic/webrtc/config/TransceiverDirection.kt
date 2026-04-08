package com.syncrobotic.webrtc.config

/**
 * Direction of media flow for a single transceiver (audio or video).
 *
 * Maps directly to the WebRTC RTCRtpTransceiverDirection values used
 * in the underlying platform APIs.
 */
enum class TransceiverDirection {
    /** Only send media to the remote peer */
    SEND_ONLY,
    /** Only receive media from the remote peer */
    RECV_ONLY,
    /** Both send and receive media */
    SEND_RECV;

    /** Whether this direction includes sending */
    val isSending: Boolean get() = this == SEND_ONLY || this == SEND_RECV

    /** Whether this direction includes receiving */
    val isReceiving: Boolean get() = this == RECV_ONLY || this == SEND_RECV
}
