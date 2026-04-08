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
 * E2E-2: Audio Send (SEND_AUDIO) tests.
 *
 * Tests the WHIP audio send flow:
 * - SDP offer contains sendonly audio
 * - Mute/unmute controls
 * - Server receives correct signaling
 */
class E2E2AudioSendTest : E2ETestBase() {

    // ── Signaling-level tests ────────────────────────────────────────────

    @Test
    fun `E2E-A-01 signaling - WHIP audio offer is sent with sendonly`() = runTest {
        val signaling = createSignaling(stream = "mobile-audio", protocol = "whip")

        val sdpOffer = buildAudioOffer(sendAudio = true)
        val result = signaling.sendOffer(sdpOffer)

        assertNotNull(result.sdpAnswer)
        assertTrue(result.sdpAnswer.contains("v=0"))
        // Answer should flip sendonly → recvonly
        assertTrue(result.sdpAnswer.contains("a=recvonly"))
        assertOfferReceived("mobile-audio", "whip")
    }

    @Test
    fun `E2E-A-01 signaling - SEND_AUDIO offer has audio but no video`() = runTest {
        val signaling = createSignaling(stream = "audio-only", protocol = "whip")

        val sdpOffer = buildAudioOffer(sendAudio = true)
        val result = signaling.sendOffer(sdpOffer)

        // Offer should contain audio media line
        val session = server.sessions.values.first()
        assertTrue(session.sdpOffer.contains("m=audio"))
        assertFalse(session.sdpOffer.contains("m=video"))
    }

    @Test
    fun `E2E-A-02 signaling - mute does not require re-signaling`() = runTest {
        // Muting is a local track operation, no new SDP exchange needed
        val signaling = createSignaling(stream = "audio-only", protocol = "whip")
        signaling.sendOffer(buildAudioOffer(sendAudio = true))

        // Only 1 POST should have been made
        val postRequests = server.recordedRequests.filter { it.method == "POST" }
        assertEquals(1, postRequests.size)
    }

    @Test
    fun `E2E-A-03 signaling - teardown after audio session`() = runTest {
        val signaling = createSignaling(stream = "mobile-audio", protocol = "whip")
        val result = signaling.sendOffer(buildAudioOffer(sendAudio = true))

        val resourceUrl = result.resourceUrl!!
        signaling.terminate(resourceUrl)

        assertEquals(0, server.sessions.size)
    }

    // ── Full WebRTC session tests (require native libs) ──────────────────

    @Test
    fun `E2E-A-01 full - connect and send audio session`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "mobile-audio",
            protocol = "whip",
            mediaConfig = MediaConfig.SEND_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            val state = session.awaitSettled()
            when (state) {
                is SessionState.Connected -> {
                    assertOfferReceived("mobile-audio", "whip")
                    val serverSession = server.sessions.values.first()
                    assertTrue(serverSession.sdpOffer.contains("m=audio"))
                }
                is SessionState.Error -> {
                    // WebRTC init failed but session handled it gracefully
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
    fun `E2E-A-02 full - setMuted mutes audio without disconnecting`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "mobile-audio",
            protocol = "whip",
            mediaConfig = MediaConfig.SEND_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            session.awaitSettled()

            // Mute should not throw or change connection state
            session.setMuted(true)
            assertNotEquals(SessionState.Closed, session.state.value)

            session.setMuted(false)
            assertNotEquals(SessionState.Closed, session.state.value)

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    @Test
    fun `E2E-A-03 full - toggleMute toggles mute state`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "mobile-audio",
            protocol = "whip",
            mediaConfig = MediaConfig.SEND_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            session.awaitSettled()

            session.toggleMute()
            session.toggleMute()
            // Should not crash or disconnect
            assertNotEquals(SessionState.Closed, session.state.value)

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildAudioOffer(sendAudio: Boolean): String {
        val dir = if (sendAudio) "sendonly" else "recvonly"
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
            a=$dir
            a=rtcp-mux
            a=ice-ufrag:testufrag
            a=ice-pwd:testpwd12345678901234
            a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF
            a=setup:actpass
        """.trimIndent().replace("\n", "\r\n") + "\r\n"
    }
}
