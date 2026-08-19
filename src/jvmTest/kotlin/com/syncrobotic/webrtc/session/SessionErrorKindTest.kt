package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.config.StreamRetryExhaustedException
import com.syncrobotic.webrtc.signaling.SignalingErrorCode
import com.syncrobotic.webrtc.signaling.SignalingException
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.test.*

/**
 * Unit tests for [SessionErrorKind] and [classifySessionError].
 */
class SessionErrorKindTest {

    private fun signaling(status: Int?, message: String = "signaling failed") = SignalingException(
        code = SignalingErrorCode.OFFER_REJECTED,
        message = message,
        httpStatus = status
    )

    // ── HTTP status mapping ────────────────────────────────────────────

    @Test
    fun `SEK-01 401 and 403 map to UNAUTHORIZED`() {
        assertEquals(SessionErrorKind.UNAUTHORIZED, classifySessionError(signaling(401)))
        assertEquals(SessionErrorKind.UNAUTHORIZED, classifySessionError(signaling(403)))
    }

    @Test
    fun `SEK-02 404 and 410 map to STREAM_UNAVAILABLE`() {
        assertEquals(SessionErrorKind.STREAM_UNAVAILABLE, classifySessionError(signaling(404)))
        assertEquals(SessionErrorKind.STREAM_UNAVAILABLE, classifySessionError(signaling(410)))
    }

    @Test
    fun `SEK-03 408 maps to TIMEOUT`() {
        assertEquals(SessionErrorKind.TIMEOUT, classifySessionError(signaling(408)))
    }

    @Test
    fun `SEK-04 other 4xx map to REJECTED`() {
        assertEquals(SessionErrorKind.REJECTED, classifySessionError(signaling(400)))
        assertEquals(SessionErrorKind.REJECTED, classifySessionError(signaling(406)))
    }

    @Test
    fun `SEK-05 5xx and rate limits map to SERVER_ERROR`() {
        assertEquals(SessionErrorKind.SERVER_ERROR, classifySessionError(signaling(500)))
        assertEquals(SessionErrorKind.SERVER_ERROR, classifySessionError(signaling(503)))
        assertEquals(SessionErrorKind.SERVER_ERROR, classifySessionError(signaling(429)))
    }

    @Test
    fun `SEK-06 signaling failure without HTTP status is a transport problem`() {
        assertEquals(
            SessionErrorKind.NETWORK,
            classifySessionError(signaling(null, "Failed to send signaling offer"))
        )
    }

    // ── Platform transport exceptions ──────────────────────────────────

    @Test
    fun `SEK-07 socket level exceptions map to NETWORK`() {
        assertEquals(SessionErrorKind.NETWORK, classifySessionError(UnknownHostException("nope")))
        assertEquals(SessionErrorKind.NETWORK, classifySessionError(ConnectException("refused")))
        assertEquals(SessionErrorKind.NETWORK, classifySessionError(IOException("broken pipe")))
    }

    @Test
    fun `SEK-08 timeout wording maps to TIMEOUT even when wrapped`() {
        val wrapped = signaling(null, "Failed to send signaling offer: connect timed out")
        assertEquals(SessionErrorKind.TIMEOUT, classifySessionError(wrapped))
    }

    // ── Caller mistakes ────────────────────────────────────────────────

    @Test
    fun `SEK-09 caller and setup mistakes map to CONFIGURATION`() {
        assertEquals(
            SessionErrorKind.CONFIGURATION,
            classifySessionError(IllegalStateException("Android Context not set"))
        )
        assertEquals(
            SessionErrorKind.CONFIGURATION,
            classifySessionError(IllegalArgumentException("bad media config"))
        )
    }

    // ── Cause chain walking ────────────────────────────────────────────

    @Test
    fun `SEK-10 classification walks the cause chain`() {
        // What connect() actually catches: retry wrapper -> signaling -> socket
        val chained = StreamRetryExhaustedException(
            message = "WebRTCSession connect failed after 5 retries",
            cause = SignalingException(
                code = SignalingErrorCode.NETWORK_ERROR,
                message = "Failed to send signaling offer",
                cause = UnknownHostException("edgecore-api.example"),
                httpStatus = null
            ),
            totalAttempts = 6
        )
        assertEquals(SessionErrorKind.NETWORK, classifySessionError(chained))
    }

    @Test
    fun `SEK-11 retry wrapper around an HTTP rejection keeps the HTTP meaning`() {
        val chained = StreamRetryExhaustedException(
            message = "WebRTCSession connect failed after 5 retries",
            cause = signaling(404),
            totalAttempts = 6
        )
        assertEquals(SessionErrorKind.STREAM_UNAVAILABLE, classifySessionError(chained))
    }

    @Test
    fun `SEK-12 deeply nested chain terminates`() {
        var e: Throwable = RuntimeException("root")
        repeat(50) { e = RuntimeException("level $it", e) }
        assertEquals(SessionErrorKind.UNKNOWN, classifySessionError(e))
    }

    @Test
    fun `SEK-13 null and unclassifiable errors map to UNKNOWN`() {
        assertEquals(SessionErrorKind.UNKNOWN, classifySessionError(null))
        assertEquals(SessionErrorKind.UNKNOWN, classifySessionError(RuntimeException("???")))
    }

    // ── Retryability ───────────────────────────────────────────────────

    @Test
    fun `SEK-14 permanent failures are not retryable`() {
        assertFalse(SessionErrorKind.UNAUTHORIZED.isRetryable)
        assertFalse(SessionErrorKind.REJECTED.isRetryable)
        assertFalse(SessionErrorKind.CONFIGURATION.isRetryable)
    }

    @Test
    fun `SEK-15 transient failures are retryable`() {
        assertTrue(SessionErrorKind.NETWORK.isRetryable)
        assertTrue(SessionErrorKind.TIMEOUT.isRetryable)
        assertTrue(SessionErrorKind.STREAM_UNAVAILABLE.isRetryable)
        assertTrue(SessionErrorKind.SERVER_ERROR.isRetryable)
        assertTrue(SessionErrorKind.UNKNOWN.isRetryable)
    }

    // ── User-facing copy ───────────────────────────────────────────────

    @Test
    fun `SEK-16 every kind has non-empty user facing copy`() {
        for (kind in SessionErrorKind.entries) {
            assertTrue(kind.userMessage.isNotBlank(), "$kind has no userMessage")
            assertTrue(kind.userHint.isNotBlank(), "$kind has no userHint")
        }
    }

    @Test
    fun `SEK-17 user facing copy never leaks the technical message`() {
        val leaky = "<html><body>Internal Server Error at /opt/mediamtx/whep</body></html>"
        val error = SessionState.Error(
            message = leaky,
            cause = signaling(500),
            isRetryable = true,
            kind = SessionErrorKind.SERVER_ERROR
        )
        assertFalse(error.userMessage.contains("html"))
        assertFalse(error.userMessage.contains("mediamtx"))
        assertFalse(error.userHint.contains("mediamtx"))
        // The raw text is still available for logs and crash reports.
        assertEquals(leaky, error.message)
    }

    @Test
    fun `SEK-18 Error derives user copy from its kind`() {
        val error = SessionState.Error(
            message = "Signaling offer rejected with HTTP 404",
            kind = SessionErrorKind.STREAM_UNAVAILABLE
        )
        assertEquals(SessionErrorKind.STREAM_UNAVAILABLE.userMessage, error.userMessage)
        assertEquals(SessionErrorKind.STREAM_UNAVAILABLE.userHint, error.userHint)
    }

    @Test
    fun `SEK-19 Error defaults to UNKNOWN kind for source compatibility`() {
        val error = SessionState.Error("something broke")
        assertEquals(SessionErrorKind.UNKNOWN, error.kind)
        assertTrue(error.isRetryable)
    }
}
