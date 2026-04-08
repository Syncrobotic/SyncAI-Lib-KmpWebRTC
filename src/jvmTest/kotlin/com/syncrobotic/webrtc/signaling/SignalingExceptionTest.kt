package com.syncrobotic.webrtc.signaling

import kotlin.test.*

/**
 * Unit tests for SignalingException and SignalingErrorCode.
 * Covers TEST_SPEC: SE-01 through SE-04.
 */
class SignalingExceptionTest {

    @Test
    fun `SE-01 default code is UNKNOWN`() {
        val ex = SignalingException(message = "something went wrong")
        assertEquals(SignalingErrorCode.UNKNOWN, ex.code)
    }

    @Test
    fun `SE-02 custom code and message`() {
        val ex = SignalingException(
            code = SignalingErrorCode.OFFER_REJECTED,
            message = "server returned 500"
        )
        assertEquals(SignalingErrorCode.OFFER_REJECTED, ex.code)
        assertEquals("server returned 500", ex.message)
    }

    @Test
    fun `SE-03 cause is preserved`() {
        val cause = RuntimeException("timeout")
        val ex = SignalingException(
            code = SignalingErrorCode.NETWORK_ERROR,
            message = "network failure",
            cause = cause
        )
        assertSame(cause, ex.cause)
    }

    @Test
    fun `SE-04 all SignalingErrorCode values exist`() {
        val values = SignalingErrorCode.entries
        assertEquals(5, values.size)
        assertTrue(values.contains(SignalingErrorCode.NETWORK_ERROR))
        assertTrue(values.contains(SignalingErrorCode.OFFER_REJECTED))
        assertTrue(values.contains(SignalingErrorCode.ICE_CANDIDATE_FAILED))
        assertTrue(values.contains(SignalingErrorCode.SESSION_TERMINATED))
        assertTrue(values.contains(SignalingErrorCode.UNKNOWN))
    }
}
