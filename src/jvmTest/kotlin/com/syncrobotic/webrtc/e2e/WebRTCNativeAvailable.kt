package com.syncrobotic.webrtc.e2e

import org.junit.Assume.assumeTrue

/**
 * Checks whether webrtc-java native libraries are fully functional on this platform.
 *
 * E2E tests that require actual WebRTC PeerConnection should call [assumeWebRTCAvailable]
 * at the start. Tests will be skipped (not failed) on machines without full native support.
 */
object WebRTCNativeAvailable {
    val isAvailable: Boolean by lazy {
        try {
            val factory = dev.onvoid.webrtc.PeerConnectionFactory()
            val config = dev.onvoid.webrtc.RTCConfiguration()
            val pc = factory.createPeerConnection(config, dev.onvoid.webrtc.PeerConnectionObserver { })
                ?: return@lazy false

            // Verify SDP creation actually works (critical for E2E tests)
            val latch = java.util.concurrent.CountDownLatch(1)
            var sdpCreated = false
            val offerOptions = dev.onvoid.webrtc.RTCOfferOptions()
            pc.createOffer(offerOptions, object : dev.onvoid.webrtc.CreateSessionDescriptionObserver {
                override fun onSuccess(description: dev.onvoid.webrtc.RTCSessionDescription) {
                    sdpCreated = description.sdp.isNotBlank()
                    latch.countDown()
                }
                override fun onFailure(error: String?) {
                    latch.countDown()
                }
            })
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

            pc.close()
            factory.dispose()
            sdpCreated
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Skip the current test if webrtc-java native libraries are not fully available.
 */
fun assumeWebRTCAvailable() {
    assumeTrue(
        "Skipped: webrtc-java native libraries not fully functional on this platform",
        WebRTCNativeAvailable.isAvailable
    )
}
