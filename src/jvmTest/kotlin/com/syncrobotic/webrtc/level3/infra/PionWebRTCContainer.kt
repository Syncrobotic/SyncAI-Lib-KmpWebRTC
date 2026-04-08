package com.syncrobotic.webrtc.level3.infra

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import java.nio.file.Path
import java.time.Duration

/**
 * Testcontainers wrapper for the Pion WebRTC IoT mock server.
 *
 * Builds from `test-infra/pion-iot/` Dockerfile and exposes:
 * - Port 8080 — HTTP (WHIP/WHEP + DataChannel endpoints)
 *
 * Usage:
 * ```kotlin
 * val pion = PionWebRTCContainer()
 * pion.start()
 *
 * val whepUrl = pion.whepUrl("camera")
 * val whipUrl = pion.whipUrl("camera")
 * ```
 */
class PionWebRTCContainer(
    dockerfilePath: Path = Path.of("test-infra/pion-iot")
) : GenericContainer<PionWebRTCContainer>(
    ImageFromDockerfile("pion-iot-test", false)
        .withDockerfile(dockerfilePath.resolve("Dockerfile"))
        .withFileFromPath(".", dockerfilePath)
) {

    init {
        withExposedPorts(HTTP_PORT)
        waitingFor(
            Wait.forHttp("/health")
                .forPort(HTTP_PORT)
                .withStartupTimeout(Duration.ofSeconds(120))
        )
    }

    /** HTTP base URL, e.g. `http://localhost:32773` */
    val httpBaseUrl: String
        get() = "http://${host}:${getMappedPort(HTTP_PORT)}"

    /** Full WHEP endpoint for a given stream */
    fun whepUrl(stream: String = "stream"): String = "$httpBaseUrl/$stream/whep"

    /** Full WHIP endpoint for a given stream */
    fun whipUrl(stream: String = "stream"): String = "$httpBaseUrl/$stream/whip"

    /** Health check URL */
    val healthUrl: String
        get() = "$httpBaseUrl/health"

    companion object {
        const val HTTP_PORT = 8080
    }
}
