package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for WebRTCConfig.
 */
class WebRTCConfigTest {

    @Test
    fun `WC-05 default ICE servers contains Google STUN`() {
        val config = WebRTCConfig.DEFAULT
        assertTrue(config.iceServers.isNotEmpty())
        assertTrue(config.iceServers.any { server ->
            server.urls.any { it.contains("stun:stun.l.google.com:19302") }
        })
    }

    @Test
    fun `WC-06 iceGatheringTimeoutMs default is 10000`() {
        val config = WebRTCConfig.DEFAULT
        assertEquals(10_000L, config.iceGatheringTimeoutMs)
    }

    @Test
    fun `WC-07 default iceMode is FULL_ICE`() {
        val config = WebRTCConfig.DEFAULT
        assertEquals(IceMode.FULL_ICE, config.iceMode)
    }
}
