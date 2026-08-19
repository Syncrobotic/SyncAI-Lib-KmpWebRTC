package com.syncrobotic.webrtc.ui

import com.syncrobotic.webrtc.session.SessionErrorKind
import com.syncrobotic.webrtc.session.SessionState
import kotlin.test.*

/**
 * Unit tests for [shouldOfferRetry] — when the overlay offers a retry affordance.
 */
class RetryAffordanceTest {

    private fun error(kind: SessionErrorKind) = SessionState.Error(
        message = "technical detail",
        isRetryable = kind.isRetryable,
        kind = kind
    )

    // ── Error state ────────────────────────────────────────────────────

    @Test
    fun `RA-01 retryable errors offer retry`() {
        for (kind in listOf(
            SessionErrorKind.NETWORK,
            SessionErrorKind.TIMEOUT,
            SessionErrorKind.STREAM_UNAVAILABLE,
            SessionErrorKind.SERVER_ERROR,
            SessionErrorKind.UNKNOWN
        )) {
            assertTrue(
                shouldOfferRetry(error(kind), prolongedReconnect = false),
                "$kind should offer retry"
            )
        }
    }

    @Test
    fun `RA-02 permanent errors do not offer retry`() {
        for (kind in listOf(
            SessionErrorKind.UNAUTHORIZED,
            SessionErrorKind.REJECTED,
            SessionErrorKind.CONFIGURATION
        )) {
            assertFalse(
                shouldOfferRetry(error(kind), prolongedReconnect = false),
                "$kind must not offer retry - the same inputs fail identically"
            )
        }
    }

    @Test
    fun `RA-03 every kind is covered by the matrix`() {
        // Guards against a new kind being added without deciding its retry behaviour.
        for (kind in SessionErrorKind.entries) {
            val offered = shouldOfferRetry(error(kind), prolongedReconnect = false)
            assertEquals(kind.isRetryable, offered, "$kind disagrees with its own isRetryable")
        }
    }

    @Test
    fun `RA-04 explicit isRetryable false wins over a retryable kind`() {
        // The field, not the kind, is the authority - callers may override it.
        val state = SessionState.Error(
            message = "x",
            isRetryable = false,
            kind = SessionErrorKind.NETWORK
        )
        assertFalse(shouldOfferRetry(state, prolongedReconnect = false))
    }

    @Test
    fun `RA-05 prolonged flag is irrelevant in the error state`() {
        val retryable = error(SessionErrorKind.NETWORK)
        assertEquals(
            shouldOfferRetry(retryable, prolongedReconnect = false),
            shouldOfferRetry(retryable, prolongedReconnect = true)
        )
    }

    // ── Reconnecting state ─────────────────────────────────────────────

    @Test
    fun `RA-06 a brief reconnect offers no retry`() {
        // The session is already retrying quickly; a button would just add noise.
        val state = SessionState.Reconnecting(attempt = 1, maxAttempts = null)
        assertFalse(shouldOfferRetry(state, prolongedReconnect = false))
    }

    @Test
    fun `RA-07 a prolonged reconnect offers retry`() {
        // This is the PERSISTENT case: unbounded retries never reach Error, so without
        // this the user would never get an actionable affordance at all.
        val state = SessionState.Reconnecting(attempt = 4, maxAttempts = null)
        assertTrue(shouldOfferRetry(state, prolongedReconnect = true))
    }

    @Test
    fun `RA-08 prolonged reconnect offers retry regardless of attempt bounds`() {
        for (max in listOf(null, 5, Int.MAX_VALUE)) {
            val state = SessionState.Reconnecting(attempt = 9, maxAttempts = max)
            assertTrue(shouldOfferRetry(state, prolongedReconnect = true), "maxAttempts=$max")
        }
    }

    // ── Every other state ──────────────────────────────────────────────

    @Test
    fun `RA-09 settled and transient states never offer retry`() {
        val states = listOf(
            SessionState.Idle,
            SessionState.Connecting,
            SessionState.Connected,
            SessionState.Closed
        )
        for (s in states) {
            assertFalse(shouldOfferRetry(s, prolongedReconnect = false), "$s (not prolonged)")
            assertFalse(shouldOfferRetry(s, prolongedReconnect = true), "$s (prolonged)")
        }
    }

    // ── Interaction with the prolonged threshold ───────────────────────

    @Test
    fun `RA-10 threshold and affordance compose as the overlay uses them`() {
        // Mirrors ReconnectingContent: prolonged is computed, then gates the button.
        val savedAttempts = WebRtcUiOptions.reconnectHintAfterAttempts
        try {
            WebRtcUiOptions.reconnectHintAfterAttempts = 3
            fun offeredAt(attempt: Int, timeReached: Boolean): Boolean {
                val state = SessionState.Reconnecting(attempt, maxAttempts = null)
                return shouldOfferRetry(state, isProlongedReconnect(attempt, timeReached))
            }
            assertFalse(offeredAt(1, false), "attempt 1, no time threshold")
            assertFalse(offeredAt(2, false), "attempt 2, no time threshold")
            assertTrue(offeredAt(3, false), "attempt 3 hits the attempt threshold")
            assertTrue(offeredAt(1, true), "attempt 1 but time threshold reached")
        } finally {
            WebRtcUiOptions.reconnectHintAfterAttempts = savedAttempts
        }
    }
}
