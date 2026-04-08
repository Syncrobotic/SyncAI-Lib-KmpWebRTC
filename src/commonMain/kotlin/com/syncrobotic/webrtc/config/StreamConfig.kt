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
        // Apply jitter: +/-jitterFactor
        val jitter = (capped * jitterFactor * (Random.nextDouble() * 2 - 1)).toLong()
        return (capped + jitter).coerceAtLeast(0)
    }

    companion object {
        /** Default retry configuration: 5 retries with exponential backoff 1s -> 2s -> 4s -> 8s -> 16s */
        val DEFAULT = RetryConfig()

        /** Aggressive retry for critical streams: 10 retries, faster first retry */
        val AGGRESSIVE = RetryConfig(
            maxRetries = 10,
            initialDelayMs = 500L,
            maxDelayMs = 60000L,
            backoffFactor = 1.5
        )

        /** Persistent retry for unattended/IoT use: unlimited retries with exponential backoff up to 45s */
        val PERSISTENT = RetryConfig(
            maxRetries = Int.MAX_VALUE,
            initialDelayMs = 1000L,
            maxDelayMs = 45000L,
            backoffFactor = 2.0
        )

        /** No automatic retry */
        val DISABLED = RetryConfig(maxRetries = 0)
    }
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
 * @param iceServers List of ICE servers for NAT traversal
 * @param iceTransportPolicy ICE transport policy ("all" or "relay")
 * @param bundlePolicy Bundle policy for media ("balanced", "max-compat", "max-bundle")
 * @param rtcpMuxPolicy RTCP multiplexing policy ("require" or "negotiate")
 * @param iceMode ICE candidate gathering mode
 * @param iceGatheringTimeoutMs Timeout for ICE gathering in milliseconds
 */
data class WebRTCConfig(
    val iceServers: List<IceServer> = IceServer.DEFAULT_ICE_SERVERS,
    val iceTransportPolicy: String = "all",
    val bundlePolicy: String = "max-bundle",
    val rtcpMuxPolicy: String = "require",
    val iceMode: IceMode = IceMode.FULL_ICE,
    val iceGatheringTimeoutMs: Long = 10_000L
) {
    companion object {
        /** Default WebRTC configuration for receiving streams */
        val DEFAULT = WebRTCConfig()

        /** Configuration for sending streams */
        val SENDER = WebRTCConfig()

        /** Configuration for bidirectional (both receive and send) */
        val BIDIRECTIONAL = WebRTCConfig()
    }
}
