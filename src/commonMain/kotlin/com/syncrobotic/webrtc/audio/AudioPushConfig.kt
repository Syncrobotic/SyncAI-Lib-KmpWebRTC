package com.syncrobotic.webrtc.audio

import com.syncrobotic.webrtc.config.IceServer
import com.syncrobotic.webrtc.config.WebRTCConfig

/**
 * Audio push configuration for WHIP streaming.
 * 
 * @param whipUrl The WHIP endpoint URL (e.g., "http://10.8.100.245:8889/mobile-audio/whip")
 * @param webrtcConfig WebRTC configuration including ICE servers
 * @param enableEchoCancellation Whether to enable acoustic echo cancellation
 * @param enableNoiseSuppression Whether to enable noise suppression
 * @param enableAutoGainControl Whether to enable automatic gain control
 * @param retryConfig Configuration for automatic reconnection
 */
data class AudioPushConfig(
    val whipUrl: String = "",
    val webrtcConfig: WebRTCConfig = WebRTCConfig.SENDER,
    val enableEchoCancellation: Boolean = true,
    val enableNoiseSuppression: Boolean = true,
    val enableAutoGainControl: Boolean = true,
    val retryConfig: AudioRetryConfig = AudioRetryConfig.DEFAULT
) {
    companion object {
        /**
         * Create an audio push config from server host and stream path.
         * 
         * @param host Server host (e.g., "10.8.100.245")
         * @param streamPath Stream path (e.g., "mobile-audio")
         * @param webrtcPort WebRTC/WHIP port (default: 8889)
         * @param useHttps Whether to use HTTPS (default: false)
         */
        fun create(
            host: String,
            streamPath: String = "mobile-audio",
            webrtcPort: Int = 8889,
            useHttps: Boolean = false
        ) = AudioPushConfig(
            whipUrl = "${if (useHttps) "https" else "http"}://$host:$webrtcPort/$streamPath/whip"
        )
        
        /**
         * Create an audio push config with custom ICE servers.
         * 
         * @param host Server host
         * @param streamPath Stream path
         * @param iceServers Custom ICE servers for NAT traversal
         */
        fun createWithIceServers(
            host: String,
            streamPath: String = "mobile-audio",
            iceServers: List<IceServer>,
            webrtcPort: Int = 8889
        ) = AudioPushConfig(
            whipUrl = "http://$host:$webrtcPort/$streamPath/whip",
            webrtcConfig = WebRTCConfig.SENDER.copy(iceServers = iceServers)
        )
    }
    
    /**
     * Create a copy with audio processing disabled (for lower latency).
     */
    fun withoutAudioProcessing() = copy(
        enableEchoCancellation = false,
        enableNoiseSuppression = false,
        enableAutoGainControl = false
    )
}

/**
 * Configuration for automatic retry/reconnection.
 * 
 * @param maxAttempts Maximum number of retry attempts (0 = no retry)
 * @param initialDelayMs Initial delay before first retry in milliseconds
 * @param maxDelayMs Maximum delay between retries in milliseconds
 * @param multiplier Multiplier for exponential backoff
 */
@Deprecated(
    message = "Use RetryConfig instead. AudioRetryConfig will be removed in v3.0.",
    replaceWith = ReplaceWith("RetryConfig", "com.syncrobotic.webrtc.config.RetryConfig")
)
data class AudioRetryConfig(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 30000L,
    val multiplier: Double = 2.0
) {
    companion object {
        /** Default retry configuration with 3 attempts */
        val DEFAULT = AudioRetryConfig()
        
        /** No automatic retry */
        val NONE = AudioRetryConfig(maxAttempts = 0)
        
        /** Aggressive retry for unstable networks */
        val AGGRESSIVE = AudioRetryConfig(
            maxAttempts = 10,
            initialDelayMs = 500L,
            maxDelayMs = 60000L
        )
    }
}
