package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for BidirectionalConfig.
 * Covers TEST_SPEC: BC-01 through BC-07.
 */
class BidirectionalConfigTest {

    @Test
    fun `BC-01 create with send path`() {
        val config = BidirectionalConfig.create(
            host = "10.0.0.1",
            receiveStreamPath = "video",
            sendStreamPath = "audio"
        )
        assertTrue(config.hasVideoReceive)
        assertTrue(config.hasAudioSend)
        assertTrue(config.isBidirectional)
    }

    @Test
    fun `BC-02 create without send path`() {
        val config = BidirectionalConfig.create(
            host = "10.0.0.1",
            receiveStreamPath = "video",
            sendStreamPath = null
        )
        assertTrue(config.hasVideoReceive)
        assertFalse(config.hasAudioSend)
        assertFalse(config.isBidirectional)
    }

    @Test
    fun `BC-03 videoOnly`() {
        val config = BidirectionalConfig.videoOnly(
            host = "10.0.0.1",
            streamPath = "video"
        )
        assertNull(config.audioConfig)
        assertFalse(config.hasAudioSend)
    }

    @Test
    fun `BC-04 audioOnly`() {
        val config = BidirectionalConfig.audioOnly(
            host = "10.0.0.1",
            streamPath = "audio"
        )
        assertNotNull(config.audioConfig)
    }

    @Test
    fun `BC-05 WHIP URL format`() {
        val config = BidirectionalConfig.create(
            host = "10.0.0.1",
            receiveStreamPath = "video",
            sendStreamPath = "audio"
        )
        assertNotNull(config.audioConfig)
        assertEquals("http://10.0.0.1:8889/audio/whip", config.audioConfig!!.whipUrl)
    }

    @Test
    fun `BC-06 HTTPS mode`() {
        val config = BidirectionalConfig.create(
            host = "10.0.0.1",
            receiveStreamPath = "video",
            sendStreamPath = "audio",
            useHttps = true
        )
        assertTrue(config.videoConfig.endpoints.webrtc.startsWith("https://"))
        assertNotNull(config.audioConfig)
        assertTrue(config.audioConfig!!.whipUrl.startsWith("https://"))
    }

    @Test
    fun `BC-07 custom ICE servers propagated`() {
        val customIce = listOf(
            IceServer(urls = listOf("turn:turn.example.com:3478"), username = "u", credential = "p")
        )
        val config = BidirectionalConfig.create(
            host = "10.0.0.1",
            receiveStreamPath = "video",
            sendStreamPath = "audio",
            iceServers = customIce
        )
        assertEquals(customIce, config.videoConfig.webrtcConfig.iceServers)
        assertNotNull(config.audioConfig)
        assertEquals(customIce, config.audioConfig!!.webrtcConfig.iceServers)
    }
}
