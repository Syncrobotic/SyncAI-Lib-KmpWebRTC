package com.syncrobotic.webrtc.level3.server

import com.syncrobotic.webrtc.level3.infra.SignalingProxyServer
import org.junit.Test

/**
 * Manual test runner for SignalingProxyServer.
 *
 * Starts a local BE signaling proxy that forwards SDP to upstream IoT devices.
 * Intended for S-2, S-5 manual testing with real Android/iOS devices.
 *
 * Prerequisites:
 *   - Docker services running: cd test-infra && docker compose up -d
 *
 * Usage:
 *   ./gradlew jvmTest --tests "com.syncrobotic.webrtc.level3.server.SignalingProxyServerTest"
 *
 * After startup, configure your test app:
 *   Signaling URL: http://<YOUR_LAN_IP>:8090/api/v1/devices/<deviceId>/offer
 */
class SignalingProxyServerTest {

    @Test
    fun `start signaling proxy for manual testing`() {
        val proxy = SignalingProxyServer(port = 8090)

        // Register upstream devices
        // mediamtx: covers S-1, C-3, C-4 style streams
        proxy.registerDevice(
            deviceId = "iot-camera",
            upstreamWhepUrl = "http://localhost:8889/iot-camera/whep",
            upstreamWhipUrl = "http://localhost:8889/iot-camera/whip"
        )
        // pion-iot: covers S-3, S-5, C-1, C-2, C-5 style streams
        proxy.registerDevice(
            deviceId = "pion-device",
            upstreamWhepUrl = "http://localhost:8080/stream/whep",
            upstreamWhipUrl = "http://localhost:8080/stream/whip"
        )

        proxy.start()

        println("==============================================")
        println("  SignalingProxy running at: ${proxy.baseUrl}")
        println()
        println("  Registered devices:")
        println("    iot-camera  → MediaMTX (port 8889)")
        println("    pion-device → Pion IoT  (port 8080)")
        println()
        println("  Test App Offer URL examples:")
        println("    ${proxy.baseUrl}/api/v1/devices/iot-camera/offer")
        println("    ${proxy.baseUrl}/api/v1/devices/pion-device/offer")
        println()
        println("  Health: ${proxy.baseUrl}/health")
        println()
        println("  Press Ctrl+C to stop...")
        println("==============================================")

        val latch = java.util.concurrent.CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(Thread {
            proxy.stop()
            println("SignalingProxy stopped.")
            latch.countDown()
        })
        latch.await()
    }
}
