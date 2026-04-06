package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for MediaConfig.
 * Covers TEST_SPEC: MC-01 through MC-13.
 */
class MediaConfigTest {

    @Test
    fun `MC-01 RECEIVE_VIDEO preset values`() {
        val config = MediaConfig.RECEIVE_VIDEO
        assertTrue(config.receiveVideo)
        assertTrue(config.receiveAudio)
        assertFalse(config.sendVideo)
        assertFalse(config.sendAudio)
    }

    @Test
    fun `MC-02 SEND_AUDIO preset`() {
        val config = MediaConfig.SEND_AUDIO
        assertFalse(config.receiveVideo)
        assertFalse(config.receiveAudio)
        assertFalse(config.sendVideo)
        assertTrue(config.sendAudio)
    }

    @Test
    fun `MC-03 SEND_VIDEO preset`() {
        val config = MediaConfig.SEND_VIDEO
        assertFalse(config.receiveVideo)
        assertFalse(config.receiveAudio)
        assertTrue(config.sendVideo)
        assertTrue(config.sendAudio)
    }

    @Test
    fun `MC-04 BIDIRECTIONAL_AUDIO preset`() {
        val config = MediaConfig.BIDIRECTIONAL_AUDIO
        assertFalse(config.receiveVideo)
        assertTrue(config.receiveAudio)
        assertFalse(config.sendVideo)
        assertTrue(config.sendAudio)
    }

    @Test
    fun `MC-05 VIDEO_CALL preset all true`() {
        val config = MediaConfig.VIDEO_CALL
        assertTrue(config.receiveVideo)
        assertTrue(config.receiveAudio)
        assertTrue(config.sendVideo)
        assertTrue(config.sendAudio)
    }

    @Test
    fun `MC-06 videoDirection returns SEND_ONLY when only sendVideo`() {
        val config = MediaConfig(sendVideo = true)
        assertEquals(TransceiverDirection.SEND_ONLY, config.videoDirection)
    }

    @Test
    fun `MC-07 videoDirection returns RECV_ONLY when only receiveVideo`() {
        val config = MediaConfig(receiveVideo = true)
        assertEquals(TransceiverDirection.RECV_ONLY, config.videoDirection)
    }

    @Test
    fun `MC-08 videoDirection returns SEND_RECV when both`() {
        val config = MediaConfig(sendVideo = true, receiveVideo = true)
        assertEquals(TransceiverDirection.SEND_RECV, config.videoDirection)
    }

    @Test
    fun `MC-09 videoDirection returns null when neither`() {
        val config = MediaConfig()
        assertNull(config.videoDirection)
    }

    @Test
    fun `MC-10 audioDirection follows same logic`() {
        assertEquals(TransceiverDirection.SEND_ONLY, MediaConfig(sendAudio = true).audioDirection)
        assertEquals(TransceiverDirection.RECV_ONLY, MediaConfig(receiveAudio = true).audioDirection)
        assertEquals(TransceiverDirection.SEND_RECV, MediaConfig(sendAudio = true, receiveAudio = true).audioDirection)
        assertNull(MediaConfig().audioDirection)
    }

    @Test
    fun `MC-11 requiresSending true when sendVideo or sendAudio`() {
        assertTrue(MediaConfig(sendVideo = true).requiresSending)
        assertTrue(MediaConfig(sendAudio = true).requiresSending)
        assertTrue(MediaConfig(sendVideo = true, sendAudio = true).requiresSending)
        assertFalse(MediaConfig(receiveVideo = true).requiresSending)
    }

    @Test
    fun `MC-12 requiresReceiving true when receiveVideo or receiveAudio`() {
        assertTrue(MediaConfig(receiveVideo = true).requiresReceiving)
        assertTrue(MediaConfig(receiveAudio = true).requiresReceiving)
        assertTrue(MediaConfig(receiveVideo = true, receiveAudio = true).requiresReceiving)
        assertFalse(MediaConfig(sendVideo = true).requiresReceiving)
    }

    @Test
    fun `MC-13 default constructor all false`() {
        val config = MediaConfig()
        assertFalse(config.receiveVideo)
        assertFalse(config.receiveAudio)
        assertFalse(config.sendVideo)
        assertFalse(config.sendAudio)
    }
}
