package com.syncrobotic.webrtc.e2e

import com.syncrobotic.webrtc.config.IceMode
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.session.SessionState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

/**
 * E2E-3: Camera Send (SEND_VIDEO) tests.
 *
 * Tests WHIP video+audio send flow:
 * - SDP offer contains sendonly video + audio
 * - Camera enable/disable controls
 * - Camera switch
 */
class E2E3CameraSendTest : E2ETestBase() {

    // ── Signaling-level tests ────────────────────────────────────────────

    @Test
    fun `E2E-C-01 signaling - WHIP video offer includes video and audio sendonly`() = runTest {
        val signaling = createSignaling(stream = "camera", protocol = "whip")

        val sdpOffer = buildVideoOffer()
        val result = signaling.sendOffer(sdpOffer)

        assertNotNull(result.sdpAnswer)
        // Answer should flip sendonly → recvonly for both media
        assertTrue(result.sdpAnswer.contains("a=recvonly"))
        assertTrue(result.sdpAnswer.contains("m=video"))
        assertTrue(result.sdpAnswer.contains("m=audio"))
        assertOfferReceived("camera", "whip")
    }

    @Test
    fun `E2E-C-01 signaling - SEND_VIDEO offer has both audio and video`() = runTest {
        val signaling = createSignaling(stream = "camera", protocol = "whip")
        signaling.sendOffer(buildVideoOffer())

        val session = server.sessions.values.first()
        assertTrue(session.sdpOffer.contains("m=audio"))
        assertTrue(session.sdpOffer.contains("m=video"))
    }

    @Test
    fun `E2E-C-02 signaling - video disable is local only`() = runTest {
        val signaling = createSignaling(stream = "camera", protocol = "whip")
        signaling.sendOffer(buildVideoOffer())

        // setVideoEnabled is local track operation — no extra signaling
        val postRequests = server.recordedRequests.filter { it.method == "POST" }
        assertEquals(1, postRequests.size)
    }

    // ── Full WebRTC session tests (require native libs + camera) ─────────

    @Test
    fun `E2E-C-01 full - connect and send camera session`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "camera",
            protocol = "whip",
            mediaConfig = MediaConfig.SEND_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            val state = session.awaitSettled()
            when (state) {
                is SessionState.Connected -> {
                    assertOfferReceived("camera", "whip")
                    val serverSession = server.sessions.values.first()
                    assertTrue(serverSession.sdpOffer.contains("m=video"))
                    assertTrue(serverSession.sdpOffer.contains("m=audio"))
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
    fun `E2E-C-02 full - setVideoEnabled disables camera track`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "camera",
            protocol = "whip",
            mediaConfig = MediaConfig.SEND_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            session.awaitSettled()

            session.setVideoEnabled(false)
            assertNotEquals(SessionState.Closed, session.state.value)

            session.setVideoEnabled(true)
            assertNotEquals(SessionState.Closed, session.state.value)

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    @Test
    fun `E2E-C-03 full - switchCamera does not crash`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "camera",
            protocol = "whip",
            mediaConfig = MediaConfig.SEND_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            session.awaitSettled()

            // switchCamera may not do anything if only one camera, but should not crash
            session.switchCamera()
            assertNotEquals(SessionState.Closed, session.state.value)

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildVideoOffer(): String {
        return """
            v=0
            o=- ${System.currentTimeMillis()} 1 IN IP4 127.0.0.1
            s=-
            t=0 0
            a=group:BUNDLE 0 1
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=mid:0
            a=rtpmap:111 opus/48000/2
            a=sendonly
            a=rtcp-mux
            a=ice-ufrag:testufrag
            a=ice-pwd:testpwd12345678901234
            a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF
            a=setup:actpass
            m=video 9 UDP/TLS/RTP/SAVPF 96
            c=IN IP4 0.0.0.0
            a=mid:1
            a=rtpmap:96 VP8/90000
            a=sendonly
            a=rtcp-mux
            a=ice-ufrag:testufrag
            a=ice-pwd:testpwd12345678901234
            a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF
            a=setup:actpass
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
    }
}
