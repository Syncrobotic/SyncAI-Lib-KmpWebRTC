package com.syncrobotic.webrtc

import kotlin.test.*

/**
 * Unit tests for AudioData.
 * Covers TEST_SPEC: AD-01 through AD-04.
 */
class AudioDataTest {

    @Test
    fun `AD-01 equals with same content`() {
        val a = AudioData(
            samples = byteArrayOf(1, 2, 3),
            sampleRate = 48000,
            channels = 1,
            timestampNs = 0L
        )
        val b = AudioData(
            samples = byteArrayOf(1, 2, 3),
            sampleRate = 48000,
            channels = 1,
            timestampNs = 999L // timestampNs not in equals
        )
        assertEquals(a, b)
    }

    @Test
    fun `AD-02 equals with different samples`() {
        val a = AudioData(byteArrayOf(1, 2, 3), 48000, 1, 0L)
        val b = AudioData(byteArrayOf(4, 5, 6), 48000, 1, 0L)
        assertNotEquals(a, b)
    }

    @Test
    fun `AD-03 equals with different sampleRate`() {
        val a = AudioData(byteArrayOf(1, 2, 3), 48000, 1, 0L)
        val b = AudioData(byteArrayOf(1, 2, 3), 16000, 1, 0L)
        assertNotEquals(a, b)
    }

    @Test
    fun `AD-04 hashCode consistency`() {
        val a = AudioData(byteArrayOf(1, 2, 3), 48000, 1, 0L)
        val b = AudioData(byteArrayOf(1, 2, 3), 48000, 1, 999L)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
