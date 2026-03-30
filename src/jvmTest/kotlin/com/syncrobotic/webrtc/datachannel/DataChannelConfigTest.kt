package com.syncrobotic.webrtc.datachannel

import kotlin.test.*

/**
 * Unit tests for DataChannelConfig.
 * Covers TEST_SPEC: DC-01 through DC-08, DCS-01, DLA-01.
 */
class DataChannelConfigTest {

    @Test
    fun `DC-01 reliable factory`() {
        val config = DataChannelConfig.reliable("msg")
        assertTrue(config.ordered)
        assertNull(config.maxRetransmits)
        assertNull(config.maxPacketLifeTimeMs)
    }

    @Test
    fun `DC-02 unreliable factory`() {
        val config = DataChannelConfig.unreliable("rt", maxRetransmits = 0)
        assertFalse(config.ordered)
        assertEquals(0, config.maxRetransmits)
    }

    @Test
    fun `DC-03 maxLifetime factory`() {
        val config = DataChannelConfig.maxLifetime("t", 500)
        assertEquals(500, config.maxPacketLifeTimeMs)
        assertNull(config.maxRetransmits)
    }

    @Test
    fun `DC-04 both maxRetransmits and maxPacketLifeTimeMs throws`() {
        assertFailsWith<IllegalArgumentException> {
            DataChannelConfig(
                label = "test",
                maxRetransmits = 3,
                maxPacketLifeTimeMs = 500
            )
        }
    }

    @Test
    fun `DC-05 negotiated true without id throws`() {
        assertFailsWith<IllegalArgumentException> {
            DataChannelConfig(
                label = "test",
                negotiated = true,
                id = null
            )
        }
    }

    @Test
    fun `DC-06 negotiated true with id is valid`() {
        val config = DataChannelConfig(
            label = "test",
            negotiated = true,
            id = 5
        )
        assertTrue(config.negotiated)
        assertEquals(5, config.id)
    }

    @Test
    fun `DC-07 default protocol`() {
        val config = DataChannelConfig.reliable("test")
        assertEquals("", config.protocol)
    }

    @Test
    fun `DC-08 default negotiated`() {
        val config = DataChannelConfig.reliable("test")
        assertFalse(config.negotiated)
    }

    // --- DataChannelState ---

    @Test
    fun `DCS-01 DataChannelState has 4 values`() {
        val values = DataChannelState.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(DataChannelState.CONNECTING))
        assertTrue(values.contains(DataChannelState.OPEN))
        assertTrue(values.contains(DataChannelState.CLOSING))
        assertTrue(values.contains(DataChannelState.CLOSED))
    }

    // --- DataChannelListenerAdapter ---

    @Test
    fun `DLA-01 all methods callable without exception`() {
        val adapter = DataChannelListenerAdapter()
        adapter.onStateChanged(DataChannelState.OPEN)
        adapter.onMessage("hello")
        adapter.onBinaryMessage(byteArrayOf(1, 2, 3))
        adapter.onBufferedAmountChange(100L)
        adapter.onError(RuntimeException("test"))
    }
}
