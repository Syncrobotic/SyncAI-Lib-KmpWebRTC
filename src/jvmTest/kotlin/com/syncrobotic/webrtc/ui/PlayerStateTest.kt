package com.syncrobotic.webrtc.ui

import kotlin.test.*

/**
 * Unit tests for PlayerState.
 * Covers TEST_SPEC: PS-01 through PS-11.
 */
class PlayerStateTest {

    @Test
    fun `PS-01 Idle displayName`() {
        assertEquals("Idle", PlayerState.Idle.displayName)
    }

    @Test
    fun `PS-02 Connecting displayName`() {
        assertEquals("Connecting...", PlayerState.Connecting.displayName)
    }

    @Test
    fun `PS-03 Loading displayName`() {
        assertEquals("Loading...", PlayerState.Loading.displayName)
    }

    @Test
    fun `PS-04 Playing displayName`() {
        assertEquals("Playing", PlayerState.Playing.displayName)
    }

    @Test
    fun `PS-05 Paused displayName`() {
        assertEquals("Paused", PlayerState.Paused.displayName)
    }

    @Test
    fun `PS-06 Buffering displayName`() {
        assertEquals("Buffering 50%", PlayerState.Buffering(50).displayName)
    }

    @Test
    fun `PS-07 Stopped displayName`() {
        assertEquals("Stopped", PlayerState.Stopped.displayName)
    }

    @Test
    fun `PS-08 Error displayName`() {
        assertEquals("Error", PlayerState.Error("fail").displayName)
    }

    @Test
    fun `PS-09 Reconnecting displayName`() {
        assertEquals("Reconnecting (2/5)...", PlayerState.Reconnecting(2, 5).displayName)
    }

    @Test
    fun `PS-10 Error message accessible`() {
        val error = PlayerState.Error("fail")
        assertEquals("fail", error.message)
    }

    @Test
    fun `PS-11 Reconnecting all properties`() {
        val state = PlayerState.Reconnecting(
            attempt = 3,
            maxAttempts = 10,
            reason = "network",
            nextRetryMs = 5000L
        )
        assertEquals(3, state.attempt)
        assertEquals(10, state.maxAttempts)
        assertEquals("network", state.reason)
        assertEquals(5000L, state.nextRetryMs)
    }
}
