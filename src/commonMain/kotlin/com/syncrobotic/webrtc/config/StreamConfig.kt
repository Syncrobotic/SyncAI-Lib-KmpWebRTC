package com.syncrobotic.webrtc.config

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Configuration for retry/reconnection behavior on stream failures.
 *
 * @param maxRetries Maximum number of retry attempts before giving up (0 = no retry)
 * @param initialDelayMs Delay before the first retry attempt in milliseconds
 * @param maxDelayMs Maximum delay cap for exponential backoff
 * @param backoffFactor Multiplier applied to delay after each attempt
 * @param retryOnDisconnect Whether to auto-reconnect when connection is temporarily lost (WebRTC DISCONNECTED)
 * @param retryOnError Whether to auto-retry when a hard error occurs
 * @param jitterFactor Random jitter range (0.0-1.0) to spread out reconnection attempts
 */
data class RetryConfig(
    val maxRetries: Int = 5,
    val initialDelayMs: Long = 1000L,
    val maxDelayMs: Long = 45000L,
    val backoffFactor: Double = 2.0,
    val retryOnDisconnect: Boolean = true,
    val retryOnError: Boolean = true,
    val jitterFactor: Double = 0.1
) {
    /**
     * Calculate the delay for the given attempt number (0-indexed).
     */
    fun calculateDelay(attempt: Int): Long {
        val baseDelay = initialDelayMs * backoffFactor.pow(attempt.toDouble())
        val capped = min(baseDelay.toLong(), maxDelayMs)
        // Apply jitter: ±jitterFactor
        val jitter = (capped * jitterFactor * (Random.nextDouble() * 2 - 1)).toLong()
        return (capped + jitter).coerceAtLeast(0)
    }

    companion object {
        /** Default retry configuration: 5 retries with exponential backoff 1s → 2s → 4s → 8s → 16s */
        val DEFAULT = RetryConfig()

        /** Aggressive retry for critical streams: 5 retries, faster first retry */
        val AGGRESSIVE = RetryConfig(
            maxRetries = 5,
            initialDelayMs = 500L,
            maxDelayMs = 10000L,
            backoffFactor = 1.5
        )

        /** No automatic retry */
        val DISABLED = RetryConfig(maxRetries = 0)
    }
}

/**
 * Supported streaming protocols.
 */
@Deprecated(
    message = "Library only supports WebRTC. StreamProtocol will be removed in v3.0."
)
enum class StreamProtocol {
    /** Real Time Streaming Protocol - low latency, widely supported */
    RTSP,
    /** HTTP Live Streaming - high compatibility, higher latency */
    HLS,
    /** Web Real-Time Communication - lowest latency, bidirectional capable */
    WEBRTC
}

/**
 * Stream direction for WebRTC connections.
 */
@Deprecated(
    message = "Direction is determined by Session type (WHEP=receive, WHIP=send). Will be removed in v3.0."
)
enum class StreamDirection {
    /** Only receive media (watching a stream) */
    RECEIVE_ONLY,
    /** Only send media (broadcasting/publishing) */
    SEND_ONLY,
    /** Bidirectional media (video call) */
    BIDIRECTIONAL
}

/**
 * ICE candidate gathering mode for WebRTC connections.
 */
enum class IceMode {
    /** Trickle ICE: send candidates individually via PATCH as they are discovered */
    TRICKLE_ICE,
    /** Full ICE: gather all candidates first, then send a single POST with the complete SDP */
    FULL_ICE
}

/**
 * WebRTC signaling transport type.
 */
@Deprecated(
    message = "Use SignalingAdapter instead. SignalingType will be removed in v3.0.",
    replaceWith = ReplaceWith("SignalingAdapter", "com.syncrobotic.webrtc.signaling.SignalingAdapter")
)
enum class SignalingType {
    /** WHEP/WHIP over HTTP(S) - standard for streaming, Cloudflare, etc. */
    WHEP_HTTP,
    /** Custom signaling over WebSocket (WSS) - for custom backends */
    WEBSOCKET
}

/**
 * WebSocket signaling configuration for custom backends.
 * 
 * @param url WebSocket URL (e.g., "wss://company.com/signaling")
 * @param streamName Stream name to watch/publish (e.g., "raw", "processed")
 * @param authToken Authentication token (optional)
 * @param clientId Client identifier (optional, auto-generated if null)
 * @param reconnectOnFailure Whether to auto-reconnect on connection loss
 * @param heartbeatIntervalMs Heartbeat interval in milliseconds (0 to disable)
 */
@Deprecated(
    message = "Use a custom SignalingAdapter implementation instead. Will be removed in v3.0."
)
data class WebSocketSignalingConfig(
    val url: String,
    val streamName: String = "raw",
    val authToken: String? = null,
    val clientId: String? = null,
    val reconnectOnFailure: Boolean = true,
    val heartbeatIntervalMs: Long = 30000L
) {
    companion object {
        fun create(
            host: String,
            streamName: String = "raw",
            path: String = "/signaling",
            secure: Boolean = true,
            authToken: String? = null
        ) = WebSocketSignalingConfig(
            url = "${if (secure) "wss" else "ws"}://$host$path",
            streamName = streamName,
            authToken = authToken
        )
    }
}

/**
 * ICE Server configuration for WebRTC.
 * 
 * @param urls STUN/TURN server URLs (e.g., "stun:stun.l.google.com:19302")
 * @param username Username for TURN authentication (optional)
 * @param credential Password for TURN authentication (optional)
 */
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null
) {
    companion object {
        /** Google's public STUN server */
        val GOOGLE_STUN = IceServer(urls = listOf("stun:stun.l.google.com:19302"))
        
        /** Default ICE servers for most use cases */
        val DEFAULT_ICE_SERVERS = listOf(GOOGLE_STUN)
    }
}

/**
 * WebRTC-specific configuration.
 * 
 * @param signalingType Type of signaling transport (HTTP or WebSocket)
 * @param iceServers List of ICE servers for NAT traversal
 * @param wsConfig WebSocket signaling config (required if signalingType is WEBSOCKET)
 * @param whepEnabled Use WHEP protocol for receiving (for HTTP signaling)
 * @param whipEnabled Use WHIP protocol for sending (for HTTP signaling)
 * @param iceTransportPolicy ICE transport policy ("all" or "relay")
 * @param bundlePolicy Bundle policy for media ("balanced", "max-compat", "max-bundle")
 * @param rtcpMuxPolicy RTCP multiplexing policy ("require" or "negotiate")
 */
data class WebRTCConfig(
    @Deprecated("Use SignalingAdapter instead. Will be removed in v3.0.")
    val signalingType: SignalingType = @Suppress("DEPRECATION") SignalingType.WHEP_HTTP,
    val iceServers: List<IceServer> = IceServer.DEFAULT_ICE_SERVERS,
    @Deprecated("Use a custom SignalingAdapter implementation instead. Will be removed in v3.0.")
    val wsConfig: WebSocketSignalingConfig? = null,
    @Deprecated("Session type determines direction. Use WhepSignalingAdapter. Will be removed in v3.0.")
    val whepEnabled: Boolean = true,
    @Deprecated("Session type determines direction. Use WhipSignalingAdapter. Will be removed in v3.0.")
    val whipEnabled: Boolean = false,
    val iceTransportPolicy: String = "all",
    val bundlePolicy: String = "max-bundle",
    val rtcpMuxPolicy: String = "require",
    val iceMode: IceMode = IceMode.FULL_ICE,
    val iceGatheringTimeoutMs: Long = 10_000L
) {
    companion object {
        /** Default WebRTC configuration for receiving streams via WHEP */
        val DEFAULT = WebRTCConfig()
        
        /** Configuration for sending streams via WHIP */
        val SENDER = WebRTCConfig(whepEnabled = false, whipEnabled = true)
        
        /** Configuration for bidirectional (both WHEP and WHIP) */
        val BIDIRECTIONAL = WebRTCConfig(whepEnabled = true, whipEnabled = true)
        
        /**
         * Create a WebSocket-based WebRTC config for custom backends.
         * 
         * @param wsUrl WebSocket URL (e.g., "wss://company.com/signaling")
         * @param streamName Stream name to watch (e.g., "raw", "processed")
         * @param authToken Authentication token
         * @param iceServers Custom ICE servers (optional)
         */
        fun websocket(
            wsUrl: String,
            streamName: String = "raw",
            authToken: String? = null,
            iceServers: List<IceServer> = IceServer.DEFAULT_ICE_SERVERS
        ) = WebRTCConfig(
            signalingType = SignalingType.WEBSOCKET,
            iceServers = iceServers,
            wsConfig = WebSocketSignalingConfig(
                url = wsUrl,
                streamName = streamName,
                authToken = authToken
            )
        )
    }
    
    /**
     * Validate the configuration.
     */
    fun validate(): Boolean {
        return when (signalingType) {
            SignalingType.WHEP_HTTP -> true
            SignalingType.WEBSOCKET -> wsConfig != null && wsConfig.url.isNotBlank()
        }
    }
}

/**
 * Server endpoints configuration - contains URLs for all supported protocols.
 * 
 * @param rtsp RTSP URL (e.g., "rtsp://10.42.0.98:8554/raw")
 * @param hls HLS URL (e.g., "http://10.42.0.98:8888/raw/index.m3u8")
 * @param webrtc WebRTC WHEP URL (e.g., "http://10.42.0.98:8889/raw")
 */
@Deprecated(
    message = "Use SignalingAdapter with direct URL instead. ServerEndpoints will be removed in v3.0."
)
data class ServerEndpoints(
    val rtsp: String,
    val hls: String,
    val webrtc: String
) {
    companion object {
        /** Default streaming server ports */
        const val DEFAULT_RTSP_PORT = 8554
        const val DEFAULT_HLS_PORT = 8888
        const val DEFAULT_WEBRTC_PORT = 8889
        
        /**
         * Create endpoints from host and stream name using default ports.
         * 
         * @param host Server host (e.g., "10.42.0.98" or "192.168.1.100")
         * @param streamName Stream name/path (e.g., "raw", "processed")
         * @param rtspPort RTSP port (default: 8554)
         * @param hlsPort HLS port (default: 8888)
         * @param webrtcPort WebRTC port (default: 8889)
         */
        fun create(
            host: String,
            streamName: String,
            rtspPort: Int = DEFAULT_RTSP_PORT,
            hlsPort: Int = DEFAULT_HLS_PORT,
            webrtcPort: Int = DEFAULT_WEBRTC_PORT
        ) = ServerEndpoints(
            rtsp = "rtsp://$host:$rtspPort/$streamName",
            hls = "http://$host:$hlsPort/$streamName/index.m3u8",
            webrtc = "http://$host:$webrtcPort/$streamName/whep"
        )
    }
}

/**
 * Configuration for video streaming.
 * 
 * @param endpoints Server endpoints for all protocols
 * @param protocol The streaming protocol to use
 * @param direction Stream direction (for WebRTC, defaults to receive only)
 * @param webrtcConfig WebRTC-specific configuration
 * @param autoPlay Whether to start playing automatically when ready
 * @param showControls Whether to show playback controls
 * @param localVideoEnabled Enable local camera for sending (future use)
 * @param localAudioEnabled Enable local microphone for sending (future use)
 */
data class StreamConfig(
    val endpoints: ServerEndpoints,
    val protocol: StreamProtocol = StreamProtocol.RTSP,
    val direction: StreamDirection = StreamDirection.RECEIVE_ONLY,
    val webrtcConfig: WebRTCConfig = WebRTCConfig.DEFAULT,
    val retryConfig: RetryConfig = RetryConfig.DEFAULT,
    val autoPlay: Boolean = true,
    val showControls: Boolean = false,
    val localVideoEnabled: Boolean = false,
    val localAudioEnabled: Boolean = false
) {
    /**
     * Get the active URL based on the selected protocol.
     */
    val url: String
        get() = when (protocol) {
            StreamProtocol.RTSP -> endpoints.rtsp
            StreamProtocol.HLS -> endpoints.hls
            StreamProtocol.WEBRTC -> {
                if (webrtcConfig.signalingType == SignalingType.WEBSOCKET) {
                    webrtcConfig.wsConfig?.url ?: endpoints.webrtc
                } else {
                    endpoints.webrtc
                }
            }
        }
    
    /**
     * Get the signaling URL for WebRTC connections.
     */
    val signalingUrl: String
        get() = when (webrtcConfig.signalingType) {
            SignalingType.WHEP_HTTP -> endpoints.webrtc
            SignalingType.WEBSOCKET -> webrtcConfig.wsConfig?.url ?: ""
        }
    
    /**
     * Check if using WebSocket signaling for WebRTC.
     */
    val isWebSocketSignaling: Boolean
        get() = protocol == StreamProtocol.WEBRTC && 
                webrtcConfig.signalingType == SignalingType.WEBSOCKET
    
    /**
     * Check if this config requires sending media (needs camera/mic permissions).
     */
    val requiresLocalMedia: Boolean
        get() = direction != StreamDirection.RECEIVE_ONLY && 
                (localVideoEnabled || localAudioEnabled)
    
    companion object {
        /**
         * Create a WebRTC config using WebSocket signaling.
         */
        fun webSocketWebRTC(
            wsUrl: String,
            streamName: String = "raw",
            authToken: String? = null,
            iceServers: List<IceServer> = IceServer.DEFAULT_ICE_SERVERS,
            direction: StreamDirection = StreamDirection.RECEIVE_ONLY,
            autoPlay: Boolean = true
        ) = StreamConfig(
            endpoints = ServerEndpoints(rtsp = "", hls = "", webrtc = wsUrl),
            protocol = StreamProtocol.WEBRTC,
            direction = direction,
            webrtcConfig = WebRTCConfig.websocket(
                wsUrl = wsUrl,
                streamName = streamName,
                authToken = authToken,
                iceServers = iceServers
            ),
            autoPlay = autoPlay
        )
        
        /**
         * Create a WebRTC config using WHEP/HTTP signaling.
         */
        fun whepWebRTC(
            host: String,
            streamName: String,
            webrtcPort: Int = ServerEndpoints.DEFAULT_WEBRTC_PORT,
            autoPlay: Boolean = true
        ) = StreamConfig(
            endpoints = ServerEndpoints.create(host, streamName, webrtcPort = webrtcPort),
            protocol = StreamProtocol.WEBRTC,
            direction = StreamDirection.RECEIVE_ONLY,
            webrtcConfig = WebRTCConfig.DEFAULT,
            autoPlay = autoPlay
        )
        
        /**
         * Create a StreamConfig directly from a full URL.
         */
        fun fromUrl(
            url: String,
            protocol: StreamProtocol? = null,
            autoPlay: Boolean = true,
            showControls: Boolean = false,
            retryConfig: RetryConfig = RetryConfig.DEFAULT
        ): StreamConfig {
            val detectedProtocol = protocol ?: detectProtocol(url)
            val parsed = parseUrl(url)
            val streamPath = extractStreamPath(parsed.path, detectedProtocol)
            
            val endpoints = ServerEndpoints(
                rtsp = "rtsp://${parsed.host}:${ServerEndpoints.DEFAULT_RTSP_PORT}/$streamPath",
                hls = "http://${parsed.host}:${ServerEndpoints.DEFAULT_HLS_PORT}/$streamPath/index.m3u8",
                webrtc = "http://${parsed.host}:${ServerEndpoints.DEFAULT_WEBRTC_PORT}/$streamPath/whep"
            )
            
            return StreamConfig(
                endpoints = endpoints,
                protocol = detectedProtocol,
                retryConfig = retryConfig,
                autoPlay = autoPlay,
                showControls = showControls
            )
        }
        
        private fun detectProtocol(url: String): StreamProtocol {
            return when {
                url.trimEnd('/').endsWith("/whep", ignoreCase = true) -> StreamProtocol.WEBRTC
                url.contains(".m3u8", ignoreCase = true) -> StreamProtocol.HLS
                url.startsWith("rtsp://", ignoreCase = true) -> StreamProtocol.RTSP
                else -> {
                    val parsed = parseUrl(url)
                    when (parsed.port) {
                        ServerEndpoints.DEFAULT_WEBRTC_PORT -> StreamProtocol.WEBRTC
                        ServerEndpoints.DEFAULT_HLS_PORT -> StreamProtocol.HLS
                        ServerEndpoints.DEFAULT_RTSP_PORT -> StreamProtocol.RTSP
                        else -> StreamProtocol.WEBRTC
                    }
                }
            }
        }
        
        private fun extractStreamPath(path: String, protocol: StreamProtocol): String {
            val cleaned = path.trim('/')
            return when (protocol) {
                StreamProtocol.WEBRTC -> {
                    if (cleaned.endsWith("/whep", ignoreCase = true)) {
                        cleaned.removeSuffix("/whep").removeSuffix("/WHEP")
                    } else cleaned
                }
                StreamProtocol.HLS -> {
                    if (cleaned.endsWith("/index.m3u8", ignoreCase = true)) {
                        cleaned.removeSuffix("/index.m3u8").removeSuffix("/INDEX.M3U8")
                    } else cleaned
                }
                StreamProtocol.RTSP -> cleaned
            }.trim('/')
        }
        
        internal data class ParsedUrl(
            val scheme: String,
            val host: String,
            val port: Int,
            val path: String
        )
        
        internal fun parseUrl(url: String): ParsedUrl {
            val schemeEnd = url.indexOf("://")
            val scheme = if (schemeEnd > 0) url.substring(0, schemeEnd) else "http"
            val afterScheme = if (schemeEnd > 0) url.substring(schemeEnd + 3) else url
            
            val pathStart = afterScheme.indexOf('/')
            val authority = if (pathStart > 0) afterScheme.substring(0, pathStart) else afterScheme
            val path = if (pathStart > 0) afterScheme.substring(pathStart) else "/"
            
            val colonIndex = authority.lastIndexOf(':')
            val host: String
            val port: Int
            if (colonIndex > 0) {
                host = authority.substring(0, colonIndex)
                port = authority.substring(colonIndex + 1).toIntOrNull() ?: defaultPort(scheme)
            } else {
                host = authority
                port = defaultPort(scheme)
            }
            
            return ParsedUrl(scheme, host, port, path)
        }
        
        private fun defaultPort(scheme: String): Int = when (scheme.lowercase()) {
            "rtsp" -> ServerEndpoints.DEFAULT_RTSP_PORT
            "http" -> 80
            "https" -> 443
            else -> 80
        }
    }
}
