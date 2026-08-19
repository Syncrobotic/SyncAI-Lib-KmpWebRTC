package com.syncrobotic.webrtc.e2e

import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import org.junit.Test
import kotlin.test.*

/**
 * E2E tests for [WebRTCSession.retryNow] against the in-process mock server.
 *
 * These are plain (non-`runTest`) tests using real-time polling, because the session's
 * retry backoff uses real delays on a real dispatcher — see [awaitCondition].
 */
class E2E8RetryNowTest : E2ETestBase() {

    /** A retry loop parked in a long backoff, so an interruption is unambiguous. */
    private fun longBackoff(maxRetries: Int = 5) = RetryConfig(
        maxRetries = maxRetries,
        initialDelayMs = 20_000,
        backoffFactor = 1.0,
        jitterFactor = 0.0
    )

    private fun offerCount(stream: String = "test") =
        server.recordedRequests.count { it.method == "POST" && it.path == "/$stream/whep" }

    private fun rejectOffers(status: Int = 500) {
        server.offerResponseOverride = OfferResponseOverride(statusCode = status, body = "rejected by test")
    }

    // ── The core behaviour ─────────────────────────────────────────────

    @Test
    fun `RN-01 retryNow interrupts a parked backoff and attempts immediately`() {
        assumeWebRTCAvailable()
        rejectOffers()
        val session = createSession(retryConfig = longBackoff())
        try {
            session.launchConnect()
            awaitCondition(description = "session parked in Reconnecting backoff") {
                session.state.value is SessionState.Reconnecting
            }
            val before = offerCount()
            val start = System.currentTimeMillis()

            session.launchRetryNow()

            awaitCondition(description = "a new offer after retryNow") { offerCount() > before }
            val elapsed = System.currentTimeMillis() - start
            assertTrue(
                elapsed < 15_000,
                "retryNow must not wait out the 20s backoff, but took ${elapsed}ms"
            )
        } finally {
            session.close()
        }
    }

    @Test
    fun `RN-02 retryNow from Idle behaves like connect`() {
        assumeWebRTCAvailable()
        rejectOffers()
        val session = createSession(retryConfig = RetryConfig.DISABLED)
        try {
            assertEquals(SessionState.Idle, session.state.value)
            session.launchRetryNow()
            awaitCondition(description = "an offer to be sent") { offerCount() > 0 }
        } finally {
            session.close()
        }
    }

    @Test
    fun `RN-03 retryNow from Error recovers to a settled state`() {
        assumeWebRTCAvailable()
        rejectOffers()
        val session = createSession(retryConfig = RetryConfig.DISABLED)
        try {
            session.launchConnect()
            val first = session.awaitSettled()
            assertTrue(first is SessionState.Error, "expected Error, got $first")

            val before = offerCount()
            session.launchRetryNow()
            awaitCondition(description = "a second offer attempt") { offerCount() > before }

            val second = session.awaitSettled()
            assertTrue(second is SessionState.Error, "expected Error again, got $second")
        } finally {
            session.close()
        }
    }

    // ── The bug this API exists to prevent ─────────────────────────────

    @Test
    fun `RN-04 rapid repeated retryNow never leaves the session stuck`() {
        assumeWebRTCAvailable()
        rejectOffers()
        // The old retryTick approach could wedge the session in Connecting forever: the
        // effect restart cancelled the in-flight connect, then its Idle/Error guard
        // refused to start a new one. retryNow() serialises instead.
        val session = createSession(retryConfig = RetryConfig.DISABLED)
        try {
            session.launchConnect()
            repeat(6) { session.launchRetryNow() }

            val settled = session.awaitSettled(timeoutMs = 20_000)
            assertTrue(
                settled is SessionState.Error || settled is SessionState.Connected,
                "session must settle, not hang; got $settled"
            )
        } finally {
            session.close()
        }
    }

    @Test
    fun `RN-05 concurrent retryNow calls all return`() {
        assumeWebRTCAvailable()
        rejectOffers()
        val session = createSession(retryConfig = RetryConfig.DISABLED)
        try {
            val jobs = (1..4).map { session.launchRetryNow() }
            awaitCondition(timeoutMs = 20_000, description = "all retryNow calls to return") {
                jobs.all { it.isCompleted }
            }
            assertTrue(jobs.none { it.isCancelled && !it.isCompleted })
        } finally {
            session.close()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    @Test
    fun `RN-06 retryNow after close is a no-op`() {
        assumeWebRTCAvailable()
        rejectOffers()
        val session = createSession(retryConfig = RetryConfig.DISABLED)
        session.launchConnect()
        session.awaitSettled()
        session.close()
        assertEquals(SessionState.Closed, session.state.value)

        val before = offerCount()
        session.launchRetryNow()
        Thread.sleep(500)

        assertEquals(SessionState.Closed, session.state.value, "close() must be terminal")
        assertEquals(before, offerCount(), "no offer may be sent after close()")
    }

    @Test
    fun `RN-07 each retryNow attempt sends a fresh offer`() {
        assumeWebRTCAvailable()
        rejectOffers()
        val session = createSession(retryConfig = RetryConfig.DISABLED)
        try {
            session.launchConnect()
            session.awaitSettled()
            val afterConnect = offerCount()
            assertTrue(afterConnect >= 1, "connect() should have sent an offer")

            session.launchRetryNow()
            awaitCondition(description = "offer #${afterConnect + 1}") { offerCount() >= afterConnect + 1 }

            session.awaitSettled()
            session.launchRetryNow()
            awaitCondition(description = "offer #${afterConnect + 2}") { offerCount() >= afterConnect + 2 }
        } finally {
            session.close()
        }
    }

    // ── State reporting the UI depends on ──────────────────────────────

    @Test
    fun `RN-08 a rejected retryable status keeps reporting Reconnecting for the overlay`() {
        assumeWebRTCAvailable()
        // 500 is retryable, so with retries configured the session must surface
        // Reconnecting - that is the state the prolonged-reconnect overlay renders.
        rejectOffers(status = 500)
        val session = createSession(retryConfig = longBackoff(maxRetries = 3))
        try {
            session.launchConnect()
            awaitCondition(description = "Reconnecting state for the overlay") {
                session.state.value is SessionState.Reconnecting
            }
            val state = session.state.value as SessionState.Reconnecting
            assertTrue(state.attempt >= 1, "attempt should be 1-indexed, got ${state.attempt}")
        } finally {
            session.close()
        }
    }

    @Test
    fun `RN-09 a permanent rejection goes straight to a non-retryable Error`() {
        assumeWebRTCAvailable()
        // 401 is not retryable, so no backoff and no retry button.
        rejectOffers(status = 401)
        val session = createSession(retryConfig = longBackoff(maxRetries = 3))
        try {
            session.launchConnect()
            val settled = session.awaitSettled()
            assertTrue(settled is SessionState.Error, "expected Error, got $settled")
            assertFalse(
                (settled as SessionState.Error).isRetryable,
                "401 must not be reported as retryable"
            )
        } finally {
            session.close()
        }
    }
}
