package com.syncrobotic.webrtc.e2e

import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.signaling.HttpSignalingAdapter
import com.syncrobotic.webrtc.signaling.SignalingAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before

/**
 * Base class for E2E tests that need a mock WHEP/WHIP server.
 *
 * Manages server lifecycle and provides helpers for creating sessions.
 */
abstract class E2ETestBase {

    protected lateinit var server: MockWhepWhipServer

    @Before
    fun startServer() {
        server = MockWhepWhipServer()
        server.start()
    }

    @After
    fun stopServer() {
        if (::server.isInitialized) {
            server.stop()
        }
    }

    // ── Session Helpers ──────────────────────────────────────────────────

    /**
     * Create a WebRTCSession pointing to the mock server.
     */
    protected fun createSession(
        stream: String = "test",
        protocol: String = "whep",
        mediaConfig: MediaConfig = MediaConfig.RECEIVE_VIDEO,
        auth: SignalingAuth = SignalingAuth.None,
        retryConfig: RetryConfig = RetryConfig.DISABLED,
        webrtcConfig: WebRTCConfig = WebRTCConfig.DEFAULT
    ): WebRTCSession {
        val url = "${server.baseUrl}/$stream/$protocol"
        val signaling = HttpSignalingAdapter(url = url, auth = auth)
        return WebRTCSession(
            signaling = signaling,
            mediaConfig = mediaConfig,
            webrtcConfig = webrtcConfig,
            retryConfig = retryConfig
        )
    }

    /**
     * Create an HttpSignalingAdapter pointing to the mock server.
     * Use this when you only need to test signaling without WebRTCSession.
     */
    protected fun createSignaling(
        stream: String = "test",
        protocol: String = "whep",
        auth: SignalingAuth = SignalingAuth.None
    ): HttpSignalingAdapter {
        val url = "${server.baseUrl}/$stream/$protocol"
        return HttpSignalingAdapter(url = url, auth = auth)
    }

    // ── Assertion Helpers ────────────────────────────────────────────────

    /**
     * Wait for the session to reach a specific state (with timeout).
     */
    protected suspend fun WebRTCSession.awaitState(
        target: Class<out SessionState>,
        timeoutMs: Long = 5000
    ): SessionState {
        return withTimeout(timeoutMs) {
            state.first { target.isInstance(it) }
        }
    }

    /**
     * Assert that the mock server received an offer for the given stream.
     */
    protected fun assertOfferReceived(stream: String, protocol: String = "whep") {
        val request = server.recordedRequests.find {
            it.method == "POST" && it.path == "/$stream/$protocol"
        }
        assert(request != null) { "Expected POST offer to /$stream/$protocol" }
        assert(request!!.contentType.contains("application/sdp")) {
            "Expected content-type application/sdp, got ${request.contentType}"
        }
    }

    /**
     * Assert that the mock server received a DELETE for the given resource.
     */
    protected fun assertSessionTerminated(resourceId: String) {
        val request = server.recordedRequests.find {
            it.method == "DELETE" && it.path == "/resource/$resourceId"
        }
        assert(request != null) { "Expected DELETE to /resource/$resourceId" }
    }
}
