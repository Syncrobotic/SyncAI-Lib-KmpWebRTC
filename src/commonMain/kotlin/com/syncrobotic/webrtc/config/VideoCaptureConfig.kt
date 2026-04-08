package com.syncrobotic.webrtc.config

/**
 * Configuration for local camera capture.
 *
 * @param width Desired capture width in pixels
 * @param height Desired capture height in pixels
 * @param fps Desired frames per second
 * @param useFrontCamera Whether to use the front-facing camera (true) or rear camera (false)
 */
data class VideoCaptureConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val useFrontCamera: Boolean = true
) {
    companion object {
        /** 720p HD capture at 30fps */
        val HD = VideoCaptureConfig(1280, 720, 30)

        /** 480p SD capture at 30fps */
        val SD = VideoCaptureConfig(640, 480, 30)

        /** 240p low-bandwidth capture at 15fps */
        val LOW = VideoCaptureConfig(320, 240, 15)
    }
}
