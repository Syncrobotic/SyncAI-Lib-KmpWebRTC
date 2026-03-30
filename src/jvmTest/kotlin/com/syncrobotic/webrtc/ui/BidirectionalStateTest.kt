package com.syncrobotic.webrtc.ui

import com.syncrobotic.webrtc.audio.AudioPushState
import kotlin.test.*

/**
 * Unit tests for BidirectionalState.
 * Covers TEST_SPEC: BS-01 through BS-06.
 */
class BidirectionalStateTest {

    @Test
    fun `BS-01 Playing and Streaming is fully connected`() {
        val state = BidirectionalState(
            videoState = PlayerState.Playing,
            audioState = AudioPushState.Streaming
        )
        assertTrue(state.isFullyConnected)
    }

    @Test
    fun `BS-02 Playing and Idle is not fully connected`() {
        val state = BidirectionalState(
            videoState = PlayerState.Playing,
            audioState = AudioPushState.Idle
        )
        assertFalse(state.isFullyConnected)
    }

    @Test
    fun `BS-03 Video Error has error`() {
        val state = BidirectionalState(
            videoState = PlayerState.Error("x"),
            audioState = AudioPushState.Idle
        )
        assertTrue(state.hasError)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Video"))
    }

    @Test
    fun `BS-04 Audio Error has error`() {
        val state = BidirectionalState(
            videoState = PlayerState.Idle,
            audioState = AudioPushState.Error("y")
        )
        assertTrue(state.hasError)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Audio"))
    }

    @Test
    fun `BS-05 both Idle no error`() {
        val state = BidirectionalState(
            videoState = PlayerState.Idle,
            audioState = AudioPushState.Idle
        )
        assertFalse(state.hasError)
        assertFalse(state.isFullyConnected)
    }

    @Test
    fun `BS-06 default state`() {
        val state = BidirectionalState()
        assertEquals(PlayerState.Idle, state.videoState)
        assertEquals(AudioPushState.Idle, state.audioState)
    }
}
