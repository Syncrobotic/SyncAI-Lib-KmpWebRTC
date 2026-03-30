package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for ServerEndpoints.
 * Covers TEST_SPEC: SE-01 through SE-02.
 */
class ServerEndpointsTest {

    @Test
    fun `SE-01 create generates correct URLs`() {
        val endpoints = ServerEndpoints.create(
            host = "10.0.0.1",
            streamName = "live"
        )
        assertEquals("rtsp://10.0.0.1:8554/live", endpoints.rtsp)
        assertEquals("http://10.0.0.1:8888/live/index.m3u8", endpoints.hls)
        assertEquals("http://10.0.0.1:8889/live/whep", endpoints.webrtc)
    }

    @Test
    fun `SE-02 default port constants`() {
        assertEquals(8554, ServerEndpoints.DEFAULT_RTSP_PORT)
        assertEquals(8888, ServerEndpoints.DEFAULT_HLS_PORT)
        assertEquals(8889, ServerEndpoints.DEFAULT_WEBRTC_PORT)
    }
}
