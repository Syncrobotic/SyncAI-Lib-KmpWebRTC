package com.syncrobotic.webrtc.e2e

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration

/**
 * Testcontainers wrapper for MediaMTX (bluenviron/mediamtx).
 *
 * Exposes:
 * - Port 8554 — RTSP
 * - Port 8889 — WebRTC (WHEP/WHIP)
 * - Port 8890 — SRT (optional)
 *
 * Usage:
 * ```kotlin
 * val mediamtx = MediaMTXContainer()
 * mediamtx.start()
 *
 * val whepUrl = "${mediamtx.webrtcBaseUrl}/stream/whep"
 * val whipUrl = "${mediamtx.webrtcBaseUrl}/stream/whip"
 * ```
 */
class MediaMTXContainer(
    image: String = "bluenviron/mediamtx:latest"
) : GenericContainer<MediaMTXContainer>(image) {

    init {
        withExposedPorts(RTSP_PORT, WEBRTC_PORT)
        // MediaMTX logs "listener opened" when ready
        // Wait for WebRTC port to be listening
        waitingFor(
            Wait.forListeningPort()
                .withStartupTimeout(Duration.ofSeconds(60))
        )
    }

    /** RTSP URL base, e.g. `rtsp://localhost:32771` */
    val rtspBaseUrl: String
        get() = "rtsp://${host}:${getMappedPort(RTSP_PORT)}"

    /** WebRTC (WHEP/WHIP) URL base, e.g. `http://localhost:32772` */
    val webrtcBaseUrl: String
        get() = "http://${host}:${getMappedPort(WEBRTC_PORT)}"

    /** Full WHEP endpoint for a given stream path */
    fun whepUrl(stream: String = "stream"): String = "$webrtcBaseUrl/$stream/whep"

    /** Full WHIP endpoint for a given stream path */
    fun whipUrl(stream: String = "stream"): String = "$webrtcBaseUrl/$stream/whip"

    /** Full RTSP URL for a given stream path */
    fun rtspUrl(stream: String = "stream"): String = "$rtspBaseUrl/$stream"

    companion object {
        const val RTSP_PORT = 8554
        const val WEBRTC_PORT = 8889
    }
}
