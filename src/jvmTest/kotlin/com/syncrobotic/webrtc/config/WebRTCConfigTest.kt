package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for WebRTCConfig.
 * Covers TEST_SPEC: WC-01 through WC-06.
 */
class WebRTCConfigTest {

    @Test
    fun `WC-01 DEFAULT config`() {
        val config = WebRTCConfig.DEFAULT
        assertEquals(SignalingType.WHEP_HTTP, config.signalingType)
        assertTrue(config.whepEnabled)
        assertFalse(config.whipEnabled)
        assertEquals(IceMode.FULL_ICE, config.iceMode)
    }

    @Test
    fun `WC-02 SENDER config`() {
        val config = WebRTCConfig.SENDER
        assertFalse(config.whepEnabled)
        assertTrue(config.whipEnabled)
    }

    @Test
    fun `WC-03 BIDIRECTIONAL config`() {
        val config = WebRTCConfig.BIDIRECTIONAL
        assertTrue(config.whepEnabled)
        assertTrue(config.whipEnabled)
    }

    @Test
    fun `WC-04 websocket factory`() {
        val config = WebRTCConfig.websocket(
            wsUrl = "wss://example.com/signaling",
            streamName = "test"
        )
        assertEquals(SignalingType.WEBSOCKET, config.signalingType)
        assertNotNull(config.wsConfig)
        assertEquals("wss://example.com/signaling", config.wsConfig!!.url)
        assertEquals("test", config.wsConfig!!.streamName)
    }

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
}
