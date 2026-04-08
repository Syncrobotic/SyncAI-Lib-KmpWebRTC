package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for VideoCaptureConfig.
 * Covers TEST_SPEC: VC-01 through VC-05.
 */
class VideoCaptureConfigTest {

    @Test
    fun `VC-01 HD preset is 1280x720 at 30fps with front camera`() {
        val config = VideoCaptureConfig.HD
        assertEquals(1280, config.width)
        assertEquals(720, config.height)
        assertEquals(30, config.fps)
        assertTrue(config.useFrontCamera)
    }

    @Test
    fun `VC-02 SD preset is 640x480 at 30fps`() {
        val config = VideoCaptureConfig.SD
        assertEquals(640, config.width)
        assertEquals(480, config.height)
        assertEquals(30, config.fps)
    }

    @Test
    fun `VC-03 LOW preset is 320x240 at 15fps`() {
        val config = VideoCaptureConfig.LOW
        assertEquals(320, config.width)
        assertEquals(240, config.height)
        assertEquals(15, config.fps)
    }

    @Test
    fun `VC-04 default values match HD`() {
        val config = VideoCaptureConfig()
        assertEquals(VideoCaptureConfig.HD.width, config.width)
        assertEquals(VideoCaptureConfig.HD.height, config.height)
        assertEquals(VideoCaptureConfig.HD.fps, config.fps)
        assertEquals(VideoCaptureConfig.HD.useFrontCamera, config.useFrontCamera)
    }

    @Test
    fun `VC-05 custom values`() {
        val config = VideoCaptureConfig(
            width = 1920,
            height = 1080,
            fps = 60,
            useFrontCamera = false
        )
        assertEquals(1920, config.width)
        assertEquals(1080, config.height)
        assertEquals(60, config.fps)
        assertFalse(config.useFrontCamera)
    }
}
