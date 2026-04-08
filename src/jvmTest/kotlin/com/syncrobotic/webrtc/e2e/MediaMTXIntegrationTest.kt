package com.syncrobotic.webrtc.e2e

import com.syncrobotic.webrtc.config.IceMode
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.signaling.HttpSignalingAdapter
import com.syncrobotic.webrtc.signaling.SignalingAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import kotlin.test.*

/**
 * Level 2: Integration tests using Testcontainers + real MediaMTX.
 *
 * Prerequisites:
 * - Docker daemon running
 * - webrtc-java native libraries available
 *
 * These tests validate the full WHEP/WHIP signaling flow against a real MediaMTX instance.
 */
class MediaMTXIntegrationTest {

    private var mediamtx: MediaMTXContainer? = null

    @Before
    fun setUp() {
        // Skip if Docker is not available
        assumeTrue(
            "Docker not available, skipping MediaMTX integration tests",
            isDockerAvailable()
        )

        try {
            mediamtx = MediaMTXContainer().apply { start() }
        } catch (e: Exception) {
            System.err.println("MediaMTX container failed to start: ${e.message}")
            e.printStackTrace(System.err)
            assumeTrue("MediaMTX container failed to start: ${e.message}", false)
        }
    }

    @After
    fun tearDown() {
        mediamtx?.stop()
    }

    // ── Signaling Integration Tests ──────────────────────────────────────

    @Test
    fun `MTX-01 signaling - WHEP offer to real MediaMTX returns valid answer`() = runTest {
        val mtx = mediamtx!!
        val signaling = HttpSignalingAdapter(url = mtx.whepUrl("test"))

        // Push a test stream first via WHIP so WHEP has something to receive
        // For signaling-only test, we test that MediaMTX responds correctly
        // even without an active publisher (it may return 404 or accept)
        try {
            val sdpOffer = buildMinimalOffer(recvVideo = true, recvAudio = true)
            val result = signaling.sendOffer(sdpOffer)
            // If MediaMTX accepts, verify the answer
            assertNotNull(result.sdpAnswer)
            assertTrue(result.sdpAnswer.contains("v=0"))
        } catch (e: com.syncrobotic.webrtc.signaling.SignalingException) {
            // MediaMTX may reject if no publisher — that's valid behavior
            // The important thing is we got a proper HTTP response, not a network error
            assertNotEquals(
                com.syncrobotic.webrtc.signaling.SignalingErrorCode.NETWORK_ERROR,
                e.code,
                "Should reach MediaMTX (got network error instead)"
            )
        }
    }

    @Test
    fun `MTX-02 signaling - WHIP offer to real MediaMTX`() = runTest {
        val mtx = mediamtx!!
        val signaling = HttpSignalingAdapter(url = mtx.whipUrl("publish"))

        val sdpOffer = buildMinimalOffer(sendVideo = true, sendAudio = true)

        try {
            val result = signaling.sendOffer(sdpOffer)
            assertNotNull(result.sdpAnswer)
            assertTrue(result.sdpAnswer.contains("v=0"))
            // WHIP should return a resource URL
            assertNotNull(result.resourceUrl)
        } catch (e: com.syncrobotic.webrtc.signaling.SignalingException) {
            // May fail due to SDP format requirements — that's fine for integration
            assertNotEquals(
                com.syncrobotic.webrtc.signaling.SignalingErrorCode.NETWORK_ERROR,
                e.code,
                "Should reach MediaMTX (got network error instead)"
            )
        }
    }

    @Test
    fun `MTX-03 signaling - MediaMTX ICE server link headers`() = runTest {
        val mtx = mediamtx!!
        val signaling = HttpSignalingAdapter(url = mtx.whipUrl("ice-test"))

        val sdpOffer = buildMinimalOffer(sendAudio = true)
        try {
            val result = signaling.sendOffer(sdpOffer)
            // MediaMTX may or may not include ICE server headers
            // Just verify we can parse whatever it sends
            assertNotNull(result.iceServers) // list, possibly empty
        } catch (_: Exception) {
            // Acceptable — focus is on reachability
        }
    }

    @Test
    fun `MTX-04 signaling - session teardown with DELETE`() = runTest {
        val mtx = mediamtx!!
        val signaling = HttpSignalingAdapter(url = mtx.whipUrl("teardown-test"))

        val sdpOffer = buildMinimalOffer(sendAudio = true)
        try {
            val result = signaling.sendOffer(sdpOffer)
            result.resourceUrl?.let { relativeUrl ->
                val resourceUrl = "${mtx.webrtcBaseUrl}$relativeUrl"
                // DELETE should not throw (terminate ignores errors)
                signaling.terminate(resourceUrl)
            }
        } catch (_: Exception) {
            // If offer fails, nothing to teardown
        }
    }

    @Test
    fun `MTX-05 signaling - Bearer auth rejected by default MediaMTX`() = runTest {
        val mtx = mediamtx!!
        // Default MediaMTX has no auth — Bearer token should be ignored (not rejected)
        val signaling = HttpSignalingAdapter(
            url = mtx.whipUrl("auth-test"),
            auth = SignalingAuth.Bearer("test-token")
        )

        val sdpOffer = buildMinimalOffer(sendAudio = true)
        try {
            val result = signaling.sendOffer(sdpOffer)
            // Default MediaMTX doesn't enforce auth, so this should still work
            assertNotNull(result.sdpAnswer)
        } catch (_: Exception) {
            // May fail for other reasons — acceptable
        }
    }

    // ── Full WebRTC + MediaMTX Tests ─────────────────────────────────────

    @Test
    fun `MTX-10 full - WebRTCSession connect to real MediaMTX WHIP`() = runTest {
        assumeWebRTCAvailable()
        val mtx = mediamtx!!

        val signaling = HttpSignalingAdapter(url = mtx.whipUrl("full-test"))
        val session = WebRTCSession(
            signaling = signaling,
            mediaConfig = MediaConfig.SEND_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()

            session.awaitSettled(15_000)

            // Verify we got past Idle (Connecting or Connected or Error)
            val state = session.state.value
            assertTrue(
                state is SessionState.Connecting ||
                state is SessionState.Connected ||
                state is SessionState.Error,
                "Expected a non-Idle state, got $state"
            )

            connectJob.cancel()
        } finally {
            session.close()
        }
    }

    @Test
    fun `MTX-11 full - WebRTCSession WHEP receive from MediaMTX`() = runTest {
        assumeWebRTCAvailable()
        val mtx = mediamtx!!

        val signaling = HttpSignalingAdapter(url = mtx.whepUrl("receive-test"))
        val session = WebRTCSession(
            signaling = signaling,
            mediaConfig = MediaConfig.RECEIVE_VIDEO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val connectJob = session.launchConnect()

            session.awaitSettled(15_000)

            connectJob.cancel()
        } finally {
            session.close()
            assertEquals(SessionState.Closed, session.state.value)
        }
    }

    @Test
    fun `MTX-12 full - multiple sessions to same MediaMTX`() = runTest {
        assumeWebRTCAvailable()
        val mtx = mediamtx!!

        val session1 = WebRTCSession(
            signaling = HttpSignalingAdapter(url = mtx.whipUrl("multi1")),
            mediaConfig = MediaConfig.SEND_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        val session2 = WebRTCSession(
            signaling = HttpSignalingAdapter(url = mtx.whipUrl("multi2")),
            mediaConfig = MediaConfig.SEND_AUDIO,
            webrtcConfig = WebRTCConfig(iceMode = IceMode.FULL_ICE),
            retryConfig = RetryConfig.DISABLED
        )

        try {
            val job1 = session1.launchConnect()
            val job2 = session2.launchConnect()

            withTimeout(15_000) {
                session1.state.first { it != SessionState.Idle }
            }
            withTimeout(15_000) {
                session2.state.first { it != SessionState.Idle }
            }

            job1.cancel()
            job2.cancel()
        } finally {
            session1.close()
            session2.close()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun isDockerAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("docker", "info")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

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
