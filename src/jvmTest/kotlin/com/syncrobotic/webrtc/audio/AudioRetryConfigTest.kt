package com.syncrobotic.webrtc.audio

import kotlin.test.*

/**
 * Unit tests for AudioRetryConfig.
 * Covers TEST_SPEC: ARC-01 through ARC-03.
 */
class AudioRetryConfigTest {

    @Test
    fun `ARC-01 DEFAULT values`() {
        val config = AudioRetryConfig.DEFAULT
        assertEquals(3, config.maxAttempts)
        assertEquals(1000L, config.initialDelayMs)
        assertEquals(30000L, config.maxDelayMs)
        assertEquals(2.0, config.multiplier)
    }

    @Test
    fun `ARC-02 NONE`() {
        val config = AudioRetryConfig.NONE
        assertEquals(0, config.maxAttempts)
    }

    @Test
    fun `ARC-03 AGGRESSIVE`() {
        val config = AudioRetryConfig.AGGRESSIVE
        assertEquals(10, config.maxAttempts)
        assertEquals(500L, config.initialDelayMs)
        assertEquals(60000L, config.maxDelayMs)
    }
}
