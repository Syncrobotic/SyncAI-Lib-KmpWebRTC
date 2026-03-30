package com.syncrobotic.webrtc

import com.syncrobotic.webrtc.config.WebRTCConfig

/**
 * WebRTC connection state.
 */
enum class WebRTCState {
    /** Initial state, not connected */
    NEW,
    /** Connecting to the peer */
    CONNECTING,
    /** Successfully connected */
    CONNECTED,
    /** Connection temporarily interrupted */
    DISCONNECTED,
    /** Connection failed */
    FAILED,
    /** Connection closed normally */
    CLOSED
}

/**
 * ICE gathering state.
 */
enum class IceGatheringState {
    NEW,
    GATHERING,
    COMPLETE
}

/**
 * ICE connection state.
 */
enum class IceConnectionState {
    NEW,
    CHECKING,
    CONNECTED,
    COMPLETED,
    FAILED,
    DISCONNECTED,
    CLOSED
}

/**
 * Signaling state.
 */
enum class SignalingState {
    STABLE,
    HAVE_LOCAL_OFFER,
    HAVE_REMOTE_OFFER,
    HAVE_LOCAL_PRANSWER,
    HAVE_REMOTE_PRANSWER,
    CLOSED
}

/**
 * Video frame data from WebRTC.
 */
data class VideoFrame(
    val width: Int,
    val height: Int,
    val timestampNs: Long,
    /** Platform-specific frame data (e.g., ByteBuffer, VideoFrame, etc.) */
    val nativeFrame: Any?
)

/**
 * Track kind enumeration.
 */
enum class TrackKind {
    VIDEO,
    AUDIO
}

/**
 * Information about a media track.
 */
data class TrackInfo(
    /** Unique track identifier */
    val trackId: String,
    /** Track type (VIDEO or AUDIO) */
    val kind: TrackKind,
    /** Whether the track is enabled */
    val enabled: Boolean,
    /** Optional label for the track */
    val label: String? = null
)

/**
 * Audio data from WebRTC.
 */
data class AudioData(
    val samples: ByteArray,
    val sampleRate: Int,
    val channels: Int,
    val timestampNs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as AudioData
        return samples.contentEquals(other.samples) &&
               sampleRate == other.sampleRate &&
               channels == other.channels
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        return result
    }
}

/**
 * WebRTC connection statistics.
 */
data class WebRTCStats(
    /** Outbound audio bitrate in bits per second */
    val audioBitrate: Long = 0,
    /** Round-trip time in milliseconds (latency) */
    val roundTripTimeMs: Double = 0.0,
    /** Jitter in milliseconds */
    val jitterMs: Double = 0.0,
    /** Total packets sent */
    val packetsSent: Long = 0,
    /** Total packets lost */
    val packetsLost: Long = 0,
    /** Audio codec being used (e.g., "opus") */
    val codec: String = "unknown",
    /** Timestamp when stats were collected */
    val timestampMs: Long = 0
) {
    /** Packet loss percentage */
    val packetLossPercent: Double
        get() = if (packetsSent > 0) (packetsLost.toDouble() / packetsSent) * 100 else 0.0

    /** Formatted bitrate display */
    val bitrateDisplay: String
        get() = when {
            audioBitrate > 1_000_000 -> "${audioBitrate / 1_000_000} Mbps"
            audioBitrate > 1_000 -> "${audioBitrate / 1_000} kbps"
            audioBitrate > 0 -> "$audioBitrate bps"
            else -> "N/A"
        }

    /** Formatted latency display */
    val latencyDisplay: String
        get() = if (roundTripTimeMs > 0) "${roundTripTimeMs.toInt()} ms" else "N/A"
}

/**
 * Callback interface for WebRTC events.
 */
interface WebRTCListener {
    /** Called when connection state changes */
    fun onConnectionStateChanged(state: WebRTCState) {}
    
    /** Called when ICE gathering state changes */
    fun onIceGatheringStateChanged(state: IceGatheringState) {}
    
    /** Called when ICE connection state changes */
    fun onIceConnectionStateChanged(state: IceConnectionState) {}
    
    /** Called when a new ICE candidate is generated */
    fun onIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {}
    
    /** Called when ICE gathering is complete */
    fun onIceGatheringComplete() {}
    
    /** Called when a video frame is received */
    fun onVideoFrame(frame: VideoFrame) {}
    
    /** Called when audio data is received */
    fun onAudioData(data: AudioData) {}
    
    /** Called when an error occurs */
    fun onError(error: String) {}
    
    /** Called when remote stream is added */
    fun onRemoteStreamAdded() {}
    
    /** Called when remote stream is removed */
    fun onRemoteStreamRemoved() {}
    
    /** Called when tracks are changed/added */
    fun onTracksChanged(
        videoTrackCount: Int,
        audioTrackCount: Int,
        tracks: List<TrackInfo>
    ) {}
}

/**
 * Cross-platform WebRTC client interface.
 * Platform implementations will wrap native WebRTC libraries.
 */
@Deprecated(
    message = "Use WhepSession/WhipSession instead. Will become internal in v3.0.",
    replaceWith = ReplaceWith("WhepSession or WhipSession", "com.syncrobotic.webrtc.session.WhepSession", "com.syncrobotic.webrtc.session.WhipSession")
)
expect class WebRTCClient {
    /**
     * Initialize the WebRTC client with configuration.
     */
    fun initialize(config: WebRTCConfig, listener: WebRTCListener)
    
    /**
     * Create an SDP offer for initiating a connection (receive mode).
     */
    suspend fun createOffer(
        receiveVideo: Boolean = true,
        receiveAudio: Boolean = true
    ): String
    
    /**
     * Create an SDP offer for sending media (WHIP).
     */
    suspend fun createSendOffer(
        sendVideo: Boolean = false,
        sendAudio: Boolean = true
    ): String
    
    /**
     * Set the remote SDP answer received from signaling.
     */
    suspend fun setRemoteAnswer(sdpAnswer: String)
    
    /**
     * Add a remote ICE candidate (trickle ICE).
     */
    suspend fun addIceCandidate(
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int
    )
    
    /**
     * Get the current local description SDP string.
     */
    fun getLocalDescription(): String?

    /**
     * Get the native video sink/renderer for platform-specific rendering.
     */
    fun getVideoSink(): Any?
    
    /**
     * Check if the client is currently connected.
     */
    val isConnected: Boolean
    
    /**
     * Get the current connection state.
     */
    val connectionState: WebRTCState
    
    /**
     * Enable or disable the local audio track (mute/unmute).
     */
    fun setAudioEnabled(enabled: Boolean)
    
    /**
     * Check if audio sending is currently enabled.
     */
    val isAudioEnabled: Boolean
    
    /**
     * Enable or disable speakerphone output.
     */
    fun setSpeakerphoneEnabled(enabled: Boolean)
    
    /**
     * Check if speakerphone output is currently enabled.
     */
    fun isSpeakerphoneEnabled(): Boolean

    /**
     * Create a Data Channel for bi-directional text/JSON messaging.
     * 
     * @param config Configuration for the data channel
     * @return The created DataChannel, or null if creation failed
     */
    fun createDataChannel(config: com.syncrobotic.webrtc.datachannel.DataChannelConfig): com.syncrobotic.webrtc.datachannel.DataChannel?

    /**
     * Get WebRTC connection statistics.
     */
    suspend fun getStats(): WebRTCStats?

    /**
     * Close the connection and release resources.
     */
    fun close()
    
    companion object {
        /**
         * Check if WebRTC is supported on this platform.
         */
        fun isSupported(): Boolean
    }
}
