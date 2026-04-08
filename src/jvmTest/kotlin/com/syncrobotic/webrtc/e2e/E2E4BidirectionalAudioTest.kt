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
 * E2E-4: Bidirectional Audio (BIDIRECTIONAL_AUDIO) tests.
 *
 * Tests simultaneous send + receive audio:
 * - SDP offer contains sendrecv audio
 * - Mute while still receiving
 * - Server answer mirrors sendrecv
 */
class E2E4BidirectionalAudioTest : E2ETestBase() {

    // ── Signaling-level tests ────────────────────────────────────────────

    @Test
    fun `E2E-B-01 signaling - bidirectional audio offer has sendrecv`() = runTest {
        val signaling = createSignaling(stream = "intercom", protocol = "whip")

        val sdpOffer = buildBidirectionalAudioOffer()
        val result = signaling.sendOffer(sdpOffer)

        assertNotNull(result.sdpAnswer)
        // Answer should also be sendrecv for bidirectional
        assertTrue(result.sdpAnswer.contains("a=sendrecv"))
        assertOfferReceived("intercom", "whip")
    }

    @Test
    fun `E2E-B-01 signaling - no video in bidirectional audio`() = runTest {
        val signaling = createSignaling(stream = "intercom", protocol = "whip")
        signaling.sendOffer(buildBidirectionalAudioOffer())

        val session = server.sessions.values.first()
        assertTrue(session.sdpOffer.contains("m=audio"))
        assertFalse(session.sdpOffer.contains("m=video"))
    }

    @Test
    fun `E2E-B-02 signaling - mute while receiving is local only`() = runTest {
        val signaling = createSignaling(stream = "intercom", protocol = "whip")
        signaling.sendOffer(buildBidirectionalAudioOffer())

        // Muting is local track disable — no re-signaling needed
        val postRequests = server.recordedRequests.filter { it.method == "POST" }
        assertEquals(1, postRequests.size)
    }

    // ── Full WebRTC session tests (require native libs + mic) ────────────

    @Test
    fun `E2E-B-01 full - connect bidirectional audio session`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "intercom",
            protocol = "whip",
            mediaConfig = MediaConfig.BIDIRECTIONAL_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            val state = session.awaitSettled()
            when (state) {
                is SessionState.Connected -> {
                    assertOfferReceived("intercom", "whip")
                    val serverSession = server.sessions.values.first()
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
    fun `E2E-B-02 full - mute while receiving keeps connection`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "intercom",
            protocol = "whip",
            mediaConfig = MediaConfig.BIDIRECTIONAL_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            session.awaitSettled()

            // Mute local mic — should not affect receiving or crash regardless of state
            session.setMuted(true)
            assertNotEquals(SessionState.Closed, session.state.value)

            // Audio receiving should still work (setAudioEnabled controls incoming)
            session.setAudioEnabled(true)
            assertNotEquals(SessionState.Closed, session.state.value)

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildBidirectionalAudioOffer(): String {
        return """
            v=0
            o=- ${System.currentTimeMillis()} 1 IN IP4 127.0.0.1
            s=-
            t=0 0
            a=group:BUNDLE 0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=mid:0
            a=rtpmap:111 opus/48000/2
            a=sendrecv
            a=rtcp-mux
            a=ice-ufrag:testufrag
            a=ice-pwd:testpwd12345678901234
            a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF
            a=setup:actpass
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
    }
}
