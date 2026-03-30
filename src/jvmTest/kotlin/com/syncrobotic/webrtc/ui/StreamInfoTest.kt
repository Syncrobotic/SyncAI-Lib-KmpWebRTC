package com.syncrobotic.webrtc.ui

import kotlin.test.*

/**
 * Unit tests for StreamInfo.
 * Covers TEST_SPEC: SI-01 through SI-08.
 */
class StreamInfoTest {

    @Test
    fun `SI-01 resolution with valid dimensions`() {
        val info = StreamInfo(width = 1920, height = 1080)
        assertEquals("1920x1080", info.resolution)
    }

    @Test
    fun `SI-02 resolution with zero dimensions`() {
        val info = StreamInfo(width = 0, height = 0)
        assertEquals("Unknown", info.resolution)
    }

    @Test
    fun `SI-03 fpsDisplay with 30f`() {
        val info = StreamInfo(fps = 30.0f)
        assertEquals("30.0 fps", info.fpsDisplay)
    }

    @Test
    fun `SI-04 fpsDisplay with 0f`() {
        val info = StreamInfo(fps = 0f)
        assertEquals("Unknown", info.fpsDisplay)
    }

    @Test
    fun `SI-05 bitrateDisplay Mbps`() {
        val info = StreamInfo(bitrate = 2_500_000L)
        assertTrue(info.bitrateDisplay.contains("Mbps"))
    }

    @Test
    fun `SI-06 bitrateDisplay Kbps`() {
        val info = StreamInfo(bitrate = 128_000L)
        assertTrue(info.bitrateDisplay.contains("Kbps"))
    }

    @Test
    fun `SI-07 bitrateDisplay bps`() {
        val info = StreamInfo(bitrate = 500L)
        assertTrue(info.bitrateDisplay.contains("bps"))
    }

    @Test
    fun `SI-08 bitrateDisplay null`() {
        val info = StreamInfo(bitrate = null)
        assertEquals("Unknown", info.bitrateDisplay)
    }
}
