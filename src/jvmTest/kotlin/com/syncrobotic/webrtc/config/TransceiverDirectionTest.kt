package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for TransceiverDirection.
 * Covers TEST_SPEC: TD-01 through TD-07.
 */
class TransceiverDirectionTest {

    @Test
    fun `TD-01 SEND_ONLY isSending is true`() {
        assertTrue(TransceiverDirection.SEND_ONLY.isSending)
    }

    @Test
    fun `TD-02 SEND_ONLY isReceiving is false`() {
        assertFalse(TransceiverDirection.SEND_ONLY.isReceiving)
    }

    @Test
    fun `TD-03 RECV_ONLY isSending is false`() {
        assertFalse(TransceiverDirection.RECV_ONLY.isSending)
    }

    @Test
    fun `TD-04 RECV_ONLY isReceiving is true`() {
        assertTrue(TransceiverDirection.RECV_ONLY.isReceiving)
    }

    @Test
    fun `TD-05 SEND_RECV isSending is true`() {
        assertTrue(TransceiverDirection.SEND_RECV.isSending)
    }

    @Test
    fun `TD-06 SEND_RECV isReceiving is true`() {
        assertTrue(TransceiverDirection.SEND_RECV.isReceiving)
    }

    @Test
    fun `TD-07 all 3 enum values exist`() {
        val values = TransceiverDirection.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(TransceiverDirection.SEND_ONLY))
        assertTrue(values.contains(TransceiverDirection.RECV_ONLY))
        assertTrue(values.contains(TransceiverDirection.SEND_RECV))
    }
}
