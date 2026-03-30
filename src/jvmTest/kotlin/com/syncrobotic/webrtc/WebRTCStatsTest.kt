package com.syncrobotic.webrtc

import kotlin.test.*

/**
 * Unit tests for WebRTCStats.
 * Covers TEST_SPEC: WS-01 through WS-08 (WebRTC Stats section).
 */
class WebRTCStatsTest {

    @Test
    fun `WS-01 packetLossPercent with losses`() {
        val stats = WebRTCStats(packetsSent = 100, packetsLost = 5)
        assertEquals(5.0, stats.packetLossPercent, 0.01)
    }

    @Test
    fun `WS-02 packetLossPercent zero sent`() {
        val stats = WebRTCStats(packetsSent = 0, packetsLost = 0)
        assertEquals(0.0, stats.packetLossPercent, 0.01)
    }

    @Test
    fun `WS-03 bitrateDisplay kbps`() {
        val stats = WebRTCStats(audioBitrate = 128_000)
        assertEquals("128 kbps", stats.bitrateDisplay)
    }

    @Test
    fun `WS-04 bitrateDisplay Mbps`() {
        val stats = WebRTCStats(audioBitrate = 2_000_000)
        assertEquals("2 Mbps", stats.bitrateDisplay)
    }

    @Test
    fun `WS-05 bitrateDisplay bps`() {
        val stats = WebRTCStats(audioBitrate = 500)
        assertEquals("500 bps", stats.bitrateDisplay)
    }

    @Test
    fun `WS-06 bitrateDisplay zero`() {
        val stats = WebRTCStats(audioBitrate = 0)
        assertEquals("N/A", stats.bitrateDisplay)
    }

    @Test
    fun `WS-07 latencyDisplay with value`() {
        val stats = WebRTCStats(roundTripTimeMs = 45.0)
        assertEquals("45 ms", stats.latencyDisplay)
    }

    @Test
    fun `WS-08 latencyDisplay zero`() {
        val stats = WebRTCStats(roundTripTimeMs = 0.0)
        assertEquals("N/A", stats.latencyDisplay)
    }
}
