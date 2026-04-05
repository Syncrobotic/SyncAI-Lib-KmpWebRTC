package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.config.MediaConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.config.WebRTCConfig
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WebAssembly/JS stub implementation of [WebRTCSession].
 * TODO: Implement using native RTCPeerConnection browser APIs.
 */
actual class WebRTCSession actual constructor(
    private val signaling: SignalingAdapter,
    private val mediaConfig: MediaConfig,
    private val webrtcConfig: WebRTCConfig,
    private val retryConfig: RetryConfig
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    actual val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<WebRTCStats?>(null)
    actual val stats: StateFlow<WebRTCStats?> = _stats.asStateFlow()

    actual suspend fun connect() {
        TODO("WebRTCSession not yet implemented for WasmJS platform")
    }

    actual fun createDataChannel(config: DataChannelConfig): DataChannel? = null
    actual fun setAudioEnabled(enabled: Boolean) {}
    actual fun setSpeakerphoneEnabled(enabled: Boolean) {}
    actual fun setMuted(muted: Boolean) {}
    actual fun toggleMute() {}
    actual fun setVideoEnabled(enabled: Boolean) {}
    actual fun switchCamera() {}
    actual fun close() {
        _state.value = SessionState.Closed
    }
}
