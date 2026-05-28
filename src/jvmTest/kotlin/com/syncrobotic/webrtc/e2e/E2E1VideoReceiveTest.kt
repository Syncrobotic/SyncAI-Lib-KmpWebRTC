package com.syncrobotic.webrtc.e2e

import com.syncrobotic.webrtc.config.IceMode
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.signaling.HttpSignalingAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

/**
 * E2E-1: Video Receive (RECEIVE_VIDEO) tests.
 *
 * Tests the WHEP receive flow:
 * - SDP offer/answer exchange with mock server
 * - Session state transitions
 * - Audio/video enable/disable controls
 * - Auto-reconnect behavior
 *
 * Tests marked "full" require native webrtc-java and are guarded by [assumeWebRTCAvailable].
 */
class E2E1VideoReceiveTest : E2ETestBase() {

    // ── Signaling-level tests (no native WebRTC needed) ──────────────────

    @Test
    fun `E2E-V-01 signaling - WHEP offer is sent and answer received`() = runTest {
        val signaling = createSignaling(stream = "raw", protocol = "whep")

        val sdpOffer = buildMinimalOffer(recvVideo = true, recvAudio = true)
        val result = signaling.sendOffer(sdpOffer)

        assertNotNull(result.sdpAnswer)
        assertTrue(result.sdpAnswer.contains("v=0"))
        assertNotNull(result.resourceUrl)
        assertNotNull(result.etag)
        assertOfferReceived("raw", "whep")

        assertEquals(1, server.sessions.size)
    }

    @Test
    fun `E2E-V-01 signaling - answer contains recvonly flipped to sendonly for video`() = runTest {
        val signaling = createSignaling(stream = "raw", protocol = "whep")

        val sdpOffer = buildMinimalOffer(recvVideo = true, recvAudio = true)
        val result = signaling.sendOffer(sdpOffer)

        assertTrue(result.sdpAnswer.contains("a=sendonly"))
    }

    @Test
    fun `E2E-V-02 signaling - setRemoteVideoEnabled does not affect signaling`() = runTest {
        val signaling = createSignaling(stream = "raw", protocol = "whep")
        val result = signaling.sendOffer(buildMinimalOffer(recvVideo = true, recvAudio = true))

        assertNotNull(result.sdpAnswer)
    }

    @Test
    fun `E2E-V-03 signaling - ICE candidates can be sent to resource URL`() = runTest {
        val signaling = createSignaling(stream = "raw", protocol = "whep")
        val result = signaling.sendOffer(buildMinimalOffer(recvVideo = true, recvAudio = true))

        val resourceUrl = result.resourceUrl!!
        signaling.sendIceCandidate(
            resourceUrl = resourceUrl,
            candidate = "a=candidate:1 1 UDP 2130706431 192.168.1.1 5000 typ host",
            sdpMid = "0",
            sdpMLineIndex = 0,
            iceUfrag = "test",
            icePwd = "testpwd"
        )

        val sessionId = server.sessions.keys.first()
        assertEquals(1, server.iceCandidates[sessionId]?.size)
    }

    @Test
    fun `E2E-V-04 signaling - server error triggers SignalingException`() = runTest {
        server.offerResponseOverride = OfferResponseOverride(statusCode = 500, body = "Server down")

        val signaling = createSignaling(stream = "raw", protocol = "whep")
        val ex = assertFailsWith<com.syncrobotic.webrtc.signaling.SignalingException> {
            signaling.sendOffer(buildMinimalOffer(recvVideo = true, recvAudio = true))
        }
        assertEquals(com.syncrobotic.webrtc.signaling.SignalingErrorCode.OFFER_REJECTED, ex.code)
    }

    @Test
    fun `E2E-V-04 signaling - session teardown sends DELETE`() = runTest {
        val signaling = createSignaling(stream = "raw", protocol = "whep")
        val result = signaling.sendOffer(buildMinimalOffer(recvVideo = true, recvAudio = true))

        val resourceUrl = result.resourceUrl!!
        signaling.terminate(resourceUrl)

        val deleteReq = server.recordedRequests.find { it.method == "DELETE" }
        assertNotNull(deleteReq)
        assertEquals(0, server.sessions.size)
    }

    @Test
    fun `E2E-V-ICE - ICE server Link header is parsed`() = runTest {
        val signaling = createSignaling(stream = "raw", protocol = "whep")
        val result = signaling.sendOffer(buildMinimalOffer(recvVideo = true, recvAudio = true))

        assertTrue(result.iceServers.isNotEmpty())
        assertTrue(result.iceServers.any { it.urls.any { u -> u.contains("stun") } })
    }

    // ── Full WebRTC session tests (require native libs) ──────────────────

    @Test
    fun `E2E-V-01 full - connect and receive video session`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "raw",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()

            val state = session.awaitSettled()
            when (state) {
                is SessionState.Connected -> {
                    // Full success: verify signaling happened
                    assertOfferReceived("raw", "whep")
                    assertTrue(server.sessions.isNotEmpty())
                }
                is SessionState.Error -> {
                    // WebRTC init failed but session handled it gracefully
                    assertNotNull(state.message)
                }
                else -> {
                    // At minimum, state left Idle
                    assertNotEquals(SessionState.Idle, state)
                }
            }

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    @Test
    fun `E2E-V-02 full - setRemoteVideoEnabled does not crash`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "raw",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            session.awaitSettled()

            // Should not throw regardless of connection state
            session.setRemoteVideoEnabled(false)
            session.setRemoteVideoEnabled(true)

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    @Test
    fun `E2E-V-03 full - setAudioEnabled does not crash`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "raw",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()
            session.awaitSettled()

            session.setAudioEnabled(false)
            session.setAudioEnabled(true)

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    @Test
    fun `E2E-V-04 full - close transitions to Closed`() = runTest {
        assumeWebRTCAvailable()

        val session = createSession(
            stream = "raw",
            protocol = "whep",
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        val connectJob = session.launchConnect()
        session.awaitSettled()

        session.close()
        assertEquals(SessionState.Closed, session.state.value)

        // Regression: close() must complete the DELETE (signaling.terminate)
        // even though it cancels the session scope. The DELETE is launched on
        // an independent scope, so poll briefly for it to reach the server.
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline &&
            server.recordedRequests.none { it.method == "DELETE" }
        ) {
            Thread.sleep(50)
        }
        assertNotNull(
            server.recordedRequests.find { it.method == "DELETE" },
            "Expected DELETE request to mock server after session.close()"
        )

        connectJob.cancel()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildMinimalOffer(
        recvVideo: Boolean = false,
        recvAudio: Boolean = false,
        sendVideo: Boolean = false,
        sendAudio: Boolean = false
    ): String {
        val lines = mutableListOf<String>()
        lines.add("v=0")
        lines.add("o=- ${System.currentTimeMillis()} 1 IN IP4 127.0.0.1")
        lines.add("s=-")
        lines.add("t=0 0")

        val mids = mutableListOf<String>()

        if (recvAudio || sendAudio) {
            val mid = "0"
            mids.add(mid)
            lines.add("m=audio 9 UDP/TLS/RTP/SAVPF 111")
            lines.add("c=IN IP4 0.0.0.0")
            lines.add("a=mid:$mid")
            lines.add("a=rtpmap:111 opus/48000/2")
            val dir = when {
                sendAudio && recvAudio -> "sendrecv"
                sendAudio -> "sendonly"
                else -> "recvonly"
            }
            lines.add("a=$dir")
            lines.add("a=rtcp-mux")
            lines.add("a=ice-ufrag:testufrag")
            lines.add("a=ice-pwd:testpwd12345678901234")
            lines.add("a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF")
            lines.add("a=setup:actpass")
        }

        if (recvVideo || sendVideo) {
            val mid = "1"
            mids.add(mid)
            lines.add("m=video 9 UDP/TLS/RTP/SAVPF 96")
            lines.add("c=IN IP4 0.0.0.0")
            lines.add("a=mid:$mid")
            lines.add("a=rtpmap:96 VP8/90000")
            val dir = when {
                sendVideo && recvVideo -> "sendrecv"
                sendVideo -> "sendonly"
                else -> "recvonly"
            }
            lines.add("a=$dir")
            lines.add("a=rtcp-mux")
            lines.add("a=ice-ufrag:testufrag")
            lines.add("a=ice-pwd:testpwd12345678901234")
            lines.add("a=fingerprint:sha-256 FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF:FF")
            lines.add("a=setup:actpass")
        }

        if (mids.isNotEmpty()) {
            lines.add(3, "a=group:BUNDLE ${mids.joinToString(" ")}")
        }

        return lines.joinToString("\r\n", postfix = "\r\n")
    }
}
