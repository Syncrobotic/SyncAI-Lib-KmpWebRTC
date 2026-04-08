package com.syncrobotic.webrtc

import com.syncrobotic.webrtc.datachannel.DataChannelState
import com.syncrobotic.webrtc.signaling.SignalingErrorCode
import kotlin.test.*

/**
 * Unit tests for enum values verification.
 * Covers TEST_SPEC: EV-01 through EV-09.
 */
class EnumValuesTest {

    @Test
    fun `EV-01 WebRTCState entries`() {
        val entries = WebRTCState.entries
        assertEquals(6, entries.size)
        assertEquals(
            listOf(
                WebRTCState.NEW,
                WebRTCState.CONNECTING,
                WebRTCState.CONNECTED,
                WebRTCState.DISCONNECTED,
                WebRTCState.FAILED,
                WebRTCState.CLOSED
            ),
            entries
        )
    }

    @Test
    fun `EV-02 IceGatheringState entries`() {
        val entries = IceGatheringState.entries
        assertEquals(3, entries.size)
        assertEquals(
            listOf(
                IceGatheringState.NEW,
                IceGatheringState.GATHERING,
                IceGatheringState.COMPLETE
            ),
            entries
        )
    }

    @Test
    fun `EV-03 IceConnectionState entries`() {
        val entries = IceConnectionState.entries
        assertEquals(7, entries.size)
        assertEquals(
            listOf(
                IceConnectionState.NEW,
                IceConnectionState.CHECKING,
                IceConnectionState.CONNECTED,
                IceConnectionState.COMPLETED,
                IceConnectionState.FAILED,
                IceConnectionState.DISCONNECTED,
                IceConnectionState.CLOSED
            ),
            entries
        )
    }

    @Test
    fun `EV-04 SignalingState entries`() {
        val entries = SignalingState.entries
        assertEquals(6, entries.size)
    }

    @Test
    fun `EV-05 TrackKind entries`() {
        val entries = TrackKind.entries
        assertEquals(2, entries.size)
        assertTrue(entries.contains(TrackKind.VIDEO))
        assertTrue(entries.contains(TrackKind.AUDIO))
    }

    @Test
    fun `EV-08 DataChannelState entries`() {
        val entries = DataChannelState.entries
        assertEquals(4, entries.size)
        assertTrue(entries.contains(DataChannelState.CONNECTING))
        assertTrue(entries.contains(DataChannelState.OPEN))
        assertTrue(entries.contains(DataChannelState.CLOSING))
        assertTrue(entries.contains(DataChannelState.CLOSED))
    }

    @Test
    fun `EV-09 SignalingErrorCode entries`() {
        val entries = SignalingErrorCode.entries
        assertEquals(5, entries.size)
        assertTrue(entries.contains(SignalingErrorCode.NETWORK_ERROR))
        assertTrue(entries.contains(SignalingErrorCode.OFFER_REJECTED))
        assertTrue(entries.contains(SignalingErrorCode.ICE_CANDIDATE_FAILED))
        assertTrue(entries.contains(SignalingErrorCode.SESSION_TERMINATED))
        assertTrue(entries.contains(SignalingErrorCode.UNKNOWN))
    }
}
