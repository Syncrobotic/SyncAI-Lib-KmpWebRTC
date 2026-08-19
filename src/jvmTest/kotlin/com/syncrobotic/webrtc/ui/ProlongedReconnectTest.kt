package com.syncrobotic.webrtc.ui

import kotlin.test.*

/**
 * Unit tests for the prolonged-reconnect threshold used by the status overlay.
 */
class ProlongedReconnectTest {

    private var savedAttempts = 0
    private var savedMs = 0L

    @BeforeTest
    fun save() {
        savedAttempts = WebRtcUiOptions.reconnectHintAfterAttempts
        savedMs = WebRtcUiOptions.reconnectHintAfterMs
    }

    @AfterTest
    fun restore() {
        WebRtcUiOptions.reconnectHintAfterAttempts = savedAttempts
        WebRtcUiOptions.reconnectHintAfterMs = savedMs
    }

    @Test
    fun `PR-01 defaults are 3 attempts and 15s`() {
        assertEquals(3, WebRtcUiOptions.reconnectHintAfterAttempts)
        assertEquals(15_000L, WebRtcUiOptions.reconnectHintAfterMs)
    }

    @Test
    fun `PR-02 early attempts show no explanation`() {
        assertFalse(isProlongedReconnect(attempt = 1, timeThresholdReached = false))
        assertFalse(isProlongedReconnect(attempt = 2, timeThresholdReached = false))
    }

    @Test
    fun `PR-03 attempt threshold alone triggers the explanation`() {
        assertTrue(isProlongedReconnect(attempt = 3, timeThresholdReached = false))
        assertTrue(isProlongedReconnect(attempt = 99, timeThresholdReached = false))
    }

    @Test
    fun `PR-04 time threshold alone triggers on the very first attempt`() {
        // A single attempt stuck behind a long backoff still deserves an explanation.
        assertTrue(isProlongedReconnect(attempt = 1, timeThresholdReached = true))
    }

    @Test
    fun `PR-05 thresholds are configurable`() {
        WebRtcUiOptions.reconnectHintAfterAttempts = 10
        assertFalse(isProlongedReconnect(attempt = 3, timeThresholdReached = false))
        assertTrue(isProlongedReconnect(attempt = 10, timeThresholdReached = false))
    }

    @Test
    fun `PR-06 an unbounded attempt count stays prolonged`() {
        // RetryConfig.PERSISTENT climbs indefinitely; the state must not flip back.
        assertTrue(isProlongedReconnect(attempt = Int.MAX_VALUE, timeThresholdReached = false))
    }
}
