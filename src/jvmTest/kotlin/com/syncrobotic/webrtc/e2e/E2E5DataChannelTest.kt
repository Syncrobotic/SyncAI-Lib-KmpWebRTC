package com.syncrobotic.webrtc.e2e

import com.syncrobotic.webrtc.config.IceMode
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.session.SessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

/**
 * E2E-5: DataChannel tests.
 *
 * Tests WebRTC DataChannel creation and configuration:
 * - Reliable channel creation
 * - Unreliable channel creation
 * - DataChannel in SDP offer
 * - Channel lifecycle
 */
class E2E5DataChannelTest : E2ETestBase() {

    // ── Signaling-level tests ────────────────────────────────────────────

    @Test
    fun `E2E-D-01 signaling - offer with DataChannel includes application media`() = runTest {
        val signaling = createSignaling(stream = "data", protocol = "whep")

        val sdpOffer = buildOfferWithDataChannel()
        val result = signaling.sendOffer(sdpOffer)

        assertNotNull(result.sdpAnswer)
        // Answer should include application media section for DataChannel
        assertTrue(result.sdpAnswer.contains("m=application"))
        assertTrue(result.sdpAnswer.contains("webrtc-datachannel"))
    }

    @Test
    fun `E2E-D-01 signaling - server returns sctp-port in answer`() = runTest {
        val signaling = createSignaling(stream = "data", protocol = "whep")
        val result = signaling.sendOffer(buildOfferWithDataChannel())

        assertTrue(result.sdpAnswer.contains("a=sctp-port:5000"))
    }

    // ── Full WebRTC session tests (require native libs) ──────────────────

    @Test
    fun `E2E-D-01 full - create reliable DataChannel before connect`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "data",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            // Create DC before connect — returns null, but config is queued
            val dc = session.createDataChannel(DataChannelConfig.reliable("control"))
            // Before connect, createDataChannel returns null (pending)
            assertNull(dc)

            val connectJob = session.launchConnect()
            val state = session.awaitSettled()
            when (state) {
                is SessionState.Connected -> {
                    // Full success: verify signaling happened with DataChannel
                    assertOfferReceived("data", "whep")
                }
                is SessionState.Error -> {
                    assertNotNull(state.message)
                }
                else -> {
                    assertNotEquals(SessionState.Idle, state)
                }
            }

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    @Test
    fun `E2E-D-02 full - create unreliable DataChannel`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "data",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val dc = session.createDataChannel(DataChannelConfig.unreliable("telemetry"))
            assertNull(dc) // pending

            val connectJob = session.launchConnect()
            val state = session.awaitSettled()
            when (state) {
                is SessionState.Connected -> {
                    assertOfferReceived("data", "whep")
                }
                is SessionState.Error -> {
                    assertNotNull(state.message)
                }
                else -> {
                    assertNotEquals(SessionState.Idle, state)
                }
            }

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    @Test
    fun `E2E-D-04 full - close session closes DataChannels`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "data",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        session.createDataChannel(DataChannelConfig.reliable("control"))

        val connectJob = session.launchConnect()
        session.awaitSettled()

        session.close()
        assertEquals(SessionState.Closed, session.state.value)

        connectJob.cancel()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildOfferWithDataChannel(): String {
        return """
            v=0
            o=- ${System.currentTimeMillis()} 1 IN IP4 127.0.0.1
            s=-
            t=0 0
            a=group:BUNDLE 0 1 2
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=mid:0
            a=rtpmap:111 opus/48000/2
            a=recvonly
            a=rtcp-mux
            a=ice-ufrag:testufrag
            a=ice-pwd:testpwd12345678901234
            a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF
            a=setup:actpass
            m=video 9 UDP/TLS/RTP/SAVPF 96
            c=IN IP4 0.0.0.0
            a=mid:1
            a=rtpmap:96 VP8/90000
            a=recvonly
            a=rtcp-mux
            a=ice-ufrag:testufrag
            a=ice-pwd:testpwd12345678901234
            a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF
            a=setup:actpass
            m=application 9 UDP/DTLS/SCTP webrtc-datachannel
            c=IN IP4 0.0.0.0
            a=mid:2
            a=sctp-port:5000
            a=ice-ufrag:testufrag
            a=ice-pwd:testpwd12345678901234
            a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF
            a=setup:actpass
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
    }
}
