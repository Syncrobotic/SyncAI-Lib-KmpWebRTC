package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for WebSocketSignalingConfig.
 * Covers TEST_SPEC: WS-01 through WS-05.
 */
class WebSocketSignalingConfigTest {

    @Test
    fun `WS-01 create with secure default`() {
        val config = WebSocketSignalingConfig.create(host = "example.com")
        assertEquals("wss://example.com/signaling", config.url)
    }

    @Test
    fun `WS-02 create with secure false`() {
        val config = WebSocketSignalingConfig.create(host = "example.com", secure = false)
        assertEquals("ws://example.com/signaling", config.url)
    }

    @Test
    fun `WS-03 create with custom path`() {
        val config = WebSocketSignalingConfig.create(host = "example.com", path = "/ws")
        assertEquals("wss://example.com/ws", config.url)
    }

    @Test
    fun `WS-04 default heartbeatIntervalMs`() {
        val config = WebSocketSignalingConfig.create(host = "example.com")
        assertEquals(30000L, config.heartbeatIntervalMs)
    }

    @Test
    fun `WS-05 default reconnectOnFailure`() {
        val config = WebSocketSignalingConfig.create(host = "example.com")
        assertTrue(config.reconnectOnFailure)
    }
}
