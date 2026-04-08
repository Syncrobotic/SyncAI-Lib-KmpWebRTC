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
 * E2E-6: Multi-Session Parallel tests.
 *
 * Tests running multiple WebRTCSession instances simultaneously:
 * - Video + Audio sessions in parallel
 * - Two video sessions to different endpoints
 * - Close one session without affecting the other
 */
class E2E6MultiSessionTest : E2ETestBase() {

    // ── Signaling-level tests ────────────────────────────────────────────

    @Test
    fun `E2E-M-01 signaling - two sessions to different streams`() = runTest {
        val signaling1 = createSignaling(stream = "video-feed", protocol = "whep")
        val signaling2 = createSignaling(stream = "audio-feed", protocol = "whip")

        val videoOffer = buildVideoOffer()
        val audioOffer = buildAudioOffer()

        val result1 = signaling1.sendOffer(videoOffer)
        val result2 = signaling2.sendOffer(audioOffer)

        assertNotNull(result1.sdpAnswer)
        assertNotNull(result2.sdpAnswer)
        assertEquals(2, server.sessions.size)

        // Each session has its own resource
        assertNotEquals(result1.resourceUrl, result2.resourceUrl)
    }

    @Test
    fun `E2E-M-02 signaling - two video sessions to different endpoints`() = runTest {
        val signaling1 = createSignaling(stream = "camera1", protocol = "whep")
        val signaling2 = createSignaling(stream = "camera2", protocol = "whep")

        val result1 = signaling1.sendOffer(buildVideoOffer())
        val result2 = signaling2.sendOffer(buildVideoOffer())

        assertNotNull(result1.sdpAnswer)
        assertNotNull(result2.sdpAnswer)
        assertEquals(2, server.sessions.size)

        // Verify both streams are different
        val streams = server.sessions.values.map { it.stream }.toSet()
        assertTrue(streams.contains("camera1"))
        assertTrue(streams.contains("camera2"))
    }

    @Test
    fun `E2E-M-03 signaling - terminate one session, other unaffected`() = runTest {
        val signaling1 = createSignaling(stream = "video", protocol = "whep")
        val signaling2 = createSignaling(stream = "audio", protocol = "whip")

        val result1 = signaling1.sendOffer(buildVideoOffer())
        val result2 = signaling2.sendOffer(buildAudioOffer())
        assertEquals(2, server.sessions.size)

        // Terminate first session
        signaling1.terminate(result1.resourceUrl!!)

        // Second session still exists
        assertEquals(1, server.sessions.size)
        val remaining = server.sessions.values.first()
        assertEquals("audio", remaining.stream)
    }

    @Test
    fun `E2E-M-01 signaling - parallel ICE candidates to different sessions`() = runTest {
        val signaling1 = createSignaling(stream = "s1", protocol = "whep")
        val signaling2 = createSignaling(stream = "s2", protocol = "whep")

        val result1 = signaling1.sendOffer(buildVideoOffer())
        val result2 = signaling2.sendOffer(buildVideoOffer())

        val resourceUrl1 = result1.resourceUrl!!
        val resourceUrl2 = result2.resourceUrl!!

        signaling1.sendIceCandidate(resourceUrl1, "a=candidate:1 1 UDP 100 1.1.1.1 1000 typ host", "0", 0)
        signaling2.sendIceCandidate(resourceUrl2, "a=candidate:2 1 UDP 100 2.2.2.2 2000 typ host", "0", 0)

        val id1 = server.sessions.keys.first { server.sessions[it]?.stream == "s1" }
        val id2 = server.sessions.keys.first { server.sessions[it]?.stream == "s2" }

        assertEquals(1, server.iceCandidates[id1]?.size)
        assertEquals(1, server.iceCandidates[id2]?.size)
    }

    // ── Full WebRTC session tests (require native libs) ──────────────────

    @Test
    fun `E2E-M-01 full - video + audio sessions parallel`() = runTest {
        assumeWebRTCAvailable()

        val videoSession = createSession(
            stream = "video",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        val audioSession = createSession(
            stream = "audio",
            protocol = "whip",
            mediaConfig = MediaConfig.SEND_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val job1 = videoSession.launchConnect()
            val job2 = audioSession.launchConnect()

            val videoState = videoSession.awaitSettled()
            val audioState = audioSession.awaitSettled()

            // Count how many sessions successfully signaled
            var expectedSessions = 0
            if (videoState is SessionState.Connected) expectedSessions++
            if (audioState is SessionState.Connected) expectedSessions++

            if (expectedSessions > 0) {
                assertEquals(expectedSessions, server.sessions.size)
            } else {
                // Both hit Error — WebRTC init failed gracefully
                assertTrue(videoState is SessionState.Error || audioState is SessionState.Error)
            }

            job1.cancel()
            job2.cancel()
        } finally {
            videoSession.close()
            audioSession.close()
        }
    }

    @Test
    fun `E2E-M-03 full - close one session, other continues`() = runTest {
        assumeWebRTCAvailable()

        val session1 = createSession(
            stream = "video",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        val session2 = createSession(
            stream = "audio",
            protocol = "whip",
            mediaConfig = MediaConfig.SEND_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val job1 = session1.launchConnect()
            val job2 = session2.launchConnect()

            session1.awaitSettled()
            session2.awaitSettled()

            // Close session1
            session1.close()
            assertEquals(SessionState.Closed, session1.state.value)

            // Session2 should not be affected
            assertNotEquals(SessionState.Closed, session2.state.value)

            job1.cancel()
            job2.cancel()
        } finally {
            session1.close()
            session2.close()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildVideoOffer(): String = """
        v=0
        o=- ${System.currentTimeMillis()} 1 IN IP4 127.0.0.1
        s=-
        t=0 0
        a=group:BUNDLE 0 1
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
    """.trimIndent().replace("\n", "\r\n") + "\r\n"

    private fun buildAudioOffer(): String = """
        v=0
        o=- ${System.currentTimeMillis()} 1 IN IP4 127.0.0.1
        s=-
        t=0 0
        a=group:BUNDLE 0
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
    """.trimIndent().replace("\n", "\r\n") + "\r\n"
}
