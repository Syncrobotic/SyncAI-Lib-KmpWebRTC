package com.syncrobotic.webrtc.config

import com.syncrobotic.webrtc.audio.AudioPushConfig
import com.syncrobotic.webrtc.audio.AudioRetryConfig

/**
 * Configuration for bidirectional WebRTC communication.
 * 
 * This combines video/audio receiving (WHEP) with audio sending (WHIP)
 * into a single convenient configuration.
 * 
 * Usage:
 * ```kotlin
 * // Simple setup with just host and stream paths
 * val config = BidirectionalConfig.create(
 *     host = "192.168.1.100",
 *     receiveStreamPath = "robot-video",      // Watch video from this stream
 *     sendStreamPath = "mobile-audio"         // Send audio to this stream
 * )
 * 
 * // Use in a composable
 * BidirectionalPlayer(
 *     config = config,
 *     modifier = Modifier.fillMaxSize(),
 *     onVideoStateChange = { state -> /* video state */ },
 *     onAudioStateChange = { state -> /* audio state */ }
 * )
 * ```
 * 
 * @param videoConfig Configuration for receiving video/audio via WHEP
 * @param audioConfig Configuration for sending audio via WHIP (null to disable)
 * @param autoStartVideo Whether to auto-start video playback
 * @param autoStartAudio Whether to auto-start audio sending
 */
data class BidirectionalConfig(
    val videoConfig: StreamConfig,
    val audioConfig: AudioPushConfig? = null,
    val autoStartVideo: Boolean = true,
    val autoStartAudio: Boolean = false
) {
    companion object {
        /**
         * Create a bidirectional config from host and stream paths.
         * 
         * @param host Server host (e.g., "192.168.1.100")
         * @param receiveStreamPath Stream path for receiving video (e.g., "robot-video")
         * @param sendStreamPath Stream path for sending audio (e.g., "mobile-audio"), null to disable
         * @param webrtcPort WebRTC/WHEP/WHIP port (default: 8889)
         * @param useHttps Whether to use HTTPS (default: false for local networks)
         * @param iceServers Custom ICE servers for NAT traversal
         * @param retryConfig Retry configuration for reconnection
         * @param autoStartVideo Whether to auto-start video playback
         * @param autoStartAudio Whether to auto-start audio sending
         */
        fun create(
            host: String,
            receiveStreamPath: String,
            sendStreamPath: String? = null,
            webrtcPort: Int = 8889,
            useHttps: Boolean = false,
            iceServers: List<IceServer> = IceServer.DEFAULT_ICE_SERVERS,
            retryConfig: RetryConfig = RetryConfig.DEFAULT,
            autoStartVideo: Boolean = true,
            autoStartAudio: Boolean = false
        ): BidirectionalConfig {
            val protocol = if (useHttps) "https" else "http"
            
            val endpoints = ServerEndpoints(
                rtsp = "rtsp://$host:8554/$receiveStreamPath",
                hls = "$protocol://$host:8888/$receiveStreamPath/index.m3u8",
                webrtc = "$protocol://$host:$webrtcPort/$receiveStreamPath"
            )
            
            val webrtcConfig = WebRTCConfig(
                signalingType = SignalingType.WHEP_HTTP,
                iceServers = iceServers
            )
            
            val videoConfig = StreamConfig(
                endpoints = endpoints,
                protocol = StreamProtocol.WEBRTC,
                webrtcConfig = webrtcConfig,
                retryConfig = retryConfig
            )
            
            val audioConfig = sendStreamPath?.let {
                AudioPushConfig(
                    whipUrl = "$protocol://$host:$webrtcPort/$sendStreamPath/whip",
                    webrtcConfig = WebRTCConfig.SENDER.copy(iceServers = iceServers),
                    retryConfig = AudioRetryConfig(
                        maxAttempts = retryConfig.maxRetries,
                        initialDelayMs = retryConfig.initialDelayMs,
                        maxDelayMs = retryConfig.maxDelayMs,
                        multiplier = retryConfig.backoffFactor
                    )
                )
            }
            
            return BidirectionalConfig(
                videoConfig = videoConfig,
                audioConfig = audioConfig,
                autoStartVideo = autoStartVideo,
                autoStartAudio = autoStartAudio
            )
        }
        
        /**
         * Create a video-only config (no audio sending).
         */
        fun videoOnly(
            host: String,
            streamPath: String,
            webrtcPort: Int = 8889,
            useHttps: Boolean = false
        ) = create(
            host = host,
            receiveStreamPath = streamPath,
            sendStreamPath = null,
            webrtcPort = webrtcPort,
            useHttps = useHttps,
            autoStartVideo = true,
            autoStartAudio = false
        )
        
        /**
         * Create an audio-only config (no video receiving).
         * Useful for voice command scenarios.
         */
        fun audioOnly(
            host: String,
            streamPath: String,
            webrtcPort: Int = 8889,
            useHttps: Boolean = false
        ): BidirectionalConfig {
            val protocol = if (useHttps) "https" else "http"
            
            return BidirectionalConfig(
                videoConfig = StreamConfig(
                    endpoints = ServerEndpoints(
                        rtsp = "",
                        hls = "",
                        webrtc = "" // Empty - no video
                    ),
                    protocol = StreamProtocol.WEBRTC
                ),
                audioConfig = AudioPushConfig(
                    whipUrl = "$protocol://$host:$webrtcPort/$streamPath/whip"
                ),
                autoStartVideo = false,
                autoStartAudio = true
            )
        }
    }
    
    /**
     * Whether this config has video receiving enabled.
     */
    val hasVideoReceive: Boolean
        get() = videoConfig.endpoints.webrtc.isNotBlank()
    
    /**
     * Whether this config has audio sending enabled.
     */
    val hasAudioSend: Boolean
        get() = audioConfig != null
    
    /**
     * Whether this config is truly bidirectional (both video receive and audio send).
     */
    val isBidirectional: Boolean
        get() = hasVideoReceive && hasAudioSend
}
