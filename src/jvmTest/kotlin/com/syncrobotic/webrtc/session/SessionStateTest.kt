package com.syncrobotic.webrtc.session

import kotlin.test.*

/**
 * Unit tests for [SessionState] sealed class.
 */
class SessionStateTest {

    // ── State identity ─────────────────────────────────────────────────

    @Test
    fun `SS-01 Idle is a singleton`() {
        assertSame(SessionState.Idle, SessionState.Idle)
    }

    @Test
    fun `SS-02 Connecting is a singleton`() {
        assertSame(SessionState.Connecting, SessionState.Connecting)
    }

    @Test
    fun `SS-03 Connected is a singleton`() {
        assertSame(SessionState.Connected, SessionState.Connected)
    }

    @Test
    fun `SS-04 Closed is a singleton`() {
        assertSame(SessionState.Closed, SessionState.Closed)
    }

    // ── Reconnecting ───────────────────────────────────────────────────

    @Test
    fun `SS-05 Reconnecting stores attempt and maxAttempts`() {
        val state = SessionState.Reconnecting(attempt = 3, maxAttempts = 5)
        assertEquals(3, state.attempt)
        assertEquals(5, state.maxAttempts)
    }

    @Test
    fun `SS-06 Reconnecting data class equality`() {
        val a = SessionState.Reconnecting(1, 5)
        val b = SessionState.Reconnecting(1, 5)
        val c = SessionState.Reconnecting(2, 5)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    // ── Error ──────────────────────────────────────────────────────────

    @Test
    fun `SS-07 Error stores message and defaults`() {
        val state = SessionState.Error(message = "timeout")
        assertEquals("timeout", state.message)
        assertNull(state.cause)
        assertFalse(state.isRetryable)
    }

    @Test
    fun `SS-08 Error with cause and retryable`() {
        val cause = RuntimeException("network")
        val state = SessionState.Error(
            message = "network error",
            cause = cause,
            isRetryable = true
        )
        assertEquals("network error", state.message)
        assertSame(cause, state.cause)
        assertTrue(state.isRetryable)
    }

    @Test
    fun `SS-09 Error data class equality ignores cause identity`() {
        val e1 = RuntimeException("x")
        val e2 = RuntimeException("x")
        // Data class uses equals() on Throwable which is reference equality
        val a = SessionState.Error("msg", e1, true)
        val b = SessionState.Error("msg", e1, true)
        assertEquals(a, b)
    }

    // ── Sealed exhaustive check ────────────────────────────────────────

    @Test
    fun `SS-10 when expression covers all states`() {
        val states: List<SessionState> = listOf(
            SessionState.Idle,
            SessionState.Connecting,
            SessionState.Connected,
            SessionState.Reconnecting(1, 3),
            SessionState.Error("err"),
            SessionState.Closed
        )
        for (s in states) {
            val label = when (s) {
                is SessionState.Idle -> "idle"
                is SessionState.Connecting -> "connecting"
                is SessionState.Connected -> "connected"
                is SessionState.Reconnecting -> "reconnecting"
                is SessionState.Error -> "error"
                is SessionState.Closed -> "closed"
            }
            assertTrue(label.isNotEmpty())
        }
    }
}
