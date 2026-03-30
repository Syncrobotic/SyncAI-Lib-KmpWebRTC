package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for StreamConfig.
 * Covers TEST_SPEC: SC-01 through SC-07.
 */
class StreamConfigTest {

    @Test
    fun `SC-01 whepWebRTC factory`() {
        val config = StreamConfig.whepWebRTC(host = "10.0.0.1", streamName = "test")
        assertTrue(config.url.contains("10.0.0.1"))
        assertTrue(config.url.contains("8889"))
        assertEquals(StreamProtocol.WEBRTC, config.protocol)
    }

    @Test
    fun `SC-02 webSocketWebRTC factory`() {
        val config = StreamConfig.webSocketWebRTC(wsUrl = "wss://example.com/ws")
        assertTrue(config.isWebSocketSignaling)
    }

    @Test
    fun `SC-03 url computed property per protocol`() {
        val endpoints = ServerEndpoints(
            rtsp = "rtsp://host:8554/stream",
            hls = "http://host:8888/stream/index.m3u8",
            webrtc = "http://host:8889/stream/whep"
        )

        val rtspConfig = StreamConfig(endpoints = endpoints, protocol = StreamProtocol.RTSP)
        assertEquals("rtsp://host:8554/stream", rtspConfig.url)

        val hlsConfig = StreamConfig(endpoints = endpoints, protocol = StreamProtocol.HLS)
        assertEquals("http://host:8888/stream/index.m3u8", hlsConfig.url)

        val webrtcConfig = StreamConfig(endpoints = endpoints, protocol = StreamProtocol.WEBRTC)
        assertEquals("http://host:8889/stream/whep", webrtcConfig.url)
    }

    @Test
    fun `SC-04 requiresLocalMedia for RECEIVE_ONLY`() {
        val config = StreamConfig(
            endpoints = ServerEndpoints.create("host", "stream"),
            direction = StreamDirection.RECEIVE_ONLY
        )
        assertFalse(config.requiresLocalMedia)
    }

    @Test
    fun `SC-05 requiresLocalMedia for SEND_ONLY with local media enabled`() {
        val config = StreamConfig(
            endpoints = ServerEndpoints.create("host", "stream"),
            direction = StreamDirection.SEND_ONLY,
            localVideoEnabled = true
        )
        assertTrue(config.requiresLocalMedia)
    }

    @Test
    fun `SC-06 requiresLocalMedia for BIDIRECTIONAL with local audio enabled`() {
        val config = StreamConfig(
            endpoints = ServerEndpoints.create("host", "stream"),
            direction = StreamDirection.BIDIRECTIONAL,
            localAudioEnabled = true
        )
        assertTrue(config.requiresLocalMedia)
    }

    @Test
    fun `SC-07 fromUrl detects WebRTC protocol`() {
        val config = StreamConfig.fromUrl("http://host:8889/stream")
        assertEquals(StreamProtocol.WEBRTC, config.protocol)
        assertNotNull(config.endpoints)
    }
}
