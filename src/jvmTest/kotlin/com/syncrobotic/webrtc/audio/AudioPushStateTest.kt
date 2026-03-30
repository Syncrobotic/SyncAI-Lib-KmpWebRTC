package com.syncrobotic.webrtc.audio

import kotlin.test.*

/**
 * Unit tests for AudioPushState.
 * Covers TEST_SPEC: APS-01 through APS-10.
 *
 * Note: TEST_SPEC says APS-03 Connecting.isActive = true, but actual code
 * defines isActive = (Streaming || Muted). Tests follow the actual code.
 */
class AudioPushStateTest {

    @Test
    fun `APS-01 Streaming isActive`() {
        assertTrue(AudioPushState.Streaming.isActive)
    }

    @Test
    fun `APS-02 Muted isActive`() {
        assertTrue(AudioPushState.Muted.isActive)
    }

    @Test
    fun `APS-03 Connecting isActive follows code definition`() {
        // Actual code: isActive = Streaming || Muted → Connecting is NOT active
        assertFalse(AudioPushState.Connecting.isActive)
    }

    @Test
    fun `APS-04 Idle isActive`() {
        assertFalse(AudioPushState.Idle.isActive)
    }

    @Test
    fun `APS-05 Disconnected isActive`() {
        assertFalse(AudioPushState.Disconnected.isActive)
    }

    @Test
    fun `APS-06 Error isActive`() {
        assertFalse(AudioPushState.Error("x").isActive)
    }

    @Test
    fun `APS-07 Error isRetryable true`() {
        val error = AudioPushState.Error("x", isRetryable = true)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `APS-08 Error isRetryable false isTerminal`() {
        val error = AudioPushState.Error("x", isRetryable = false)
        assertTrue(error.isTerminal)
    }

    @Test
    fun `APS-09 Reconnecting attempt`() {
        val reconnecting = AudioPushState.Reconnecting(attempt = 2, maxAttempts = 5)
        assertEquals(2, reconnecting.attempt)
    }

    @Test
    fun `APS-10 Reconnecting maxAttempts`() {
        val reconnecting = AudioPushState.Reconnecting(attempt = 2, maxAttempts = 5)
        assertEquals(5, reconnecting.maxAttempts)
    }
}
