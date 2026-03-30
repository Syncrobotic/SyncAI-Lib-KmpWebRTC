package com.syncrobotic.webrtc.audio

import com.syncrobotic.webrtc.config.IceServer
import com.syncrobotic.webrtc.config.WebRTCConfig
import kotlin.test.*

/**
 * Unit tests for AudioPushConfig.
 * Covers TEST_SPEC: APC-01 through APC-07.
 */
class AudioPushConfigTest {

    @Test
    fun `APC-01 create with default params`() {
        val config = AudioPushConfig.create(host = "10.0.0.1", streamPath = "audio")
        assertEquals("http://10.0.0.1:8889/audio/whip", config.whipUrl)
    }

    @Test
    fun `APC-02 create with useHttps`() {
        val config = AudioPushConfig.create(host = "10.0.0.1", streamPath = "audio", useHttps = true)
        assertEquals("https://10.0.0.1:8889/audio/whip", config.whipUrl)
    }

    @Test
    fun `APC-03 create with custom port`() {
        val config = AudioPushConfig.create(host = "10.0.0.1", streamPath = "audio", webrtcPort = 9000)
        assertTrue(config.whipUrl.contains(":9000/"))
    }

    @Test
    fun `APC-04 createWithIceServers`() {
        val iceServers = listOf(
            IceServer(urls = listOf("turn:turn.example.com"), username = "u", credential = "p")
        )
        val config = AudioPushConfig.createWithIceServers(
            host = "10.0.0.1",
            streamPath = "audio",
            iceServers = iceServers
        )
        assertEquals(iceServers, config.webrtcConfig.iceServers)
    }

    @Test
    fun `APC-05 withoutAudioProcessing`() {
        val config = AudioPushConfig.create(host = "10.0.0.1").withoutAudioProcessing()
        assertFalse(config.enableEchoCancellation)
        assertFalse(config.enableNoiseSuppression)
        assertFalse(config.enableAutoGainControl)
    }

    @Test
    fun `APC-06 default audio processing enabled`() {
        val config = AudioPushConfig.create(host = "10.0.0.1")
        assertTrue(config.enableEchoCancellation)
        assertTrue(config.enableNoiseSuppression)
        assertTrue(config.enableAutoGainControl)
    }

    @Test
    fun `APC-07 default webrtcConfig is SENDER`() {
        val config = AudioPushConfig.create(host = "10.0.0.1")
        assertEquals(WebRTCConfig.SENDER, config.webrtcConfig)
    }
}
