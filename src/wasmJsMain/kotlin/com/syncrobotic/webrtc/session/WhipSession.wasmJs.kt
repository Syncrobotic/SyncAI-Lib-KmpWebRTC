package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.audio.AudioPushConfig
import com.syncrobotic.webrtc.config.RetryConfig
import com.syncrobotic.webrtc.datachannel.DataChannel
import com.syncrobotic.webrtc.datachannel.DataChannelConfig
import com.syncrobotic.webrtc.signaling.SignalingAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * WasmJS/Browser implementation of [WhipSession].
 */
actual class WhipSession actual constructor(
    private val signaling: SignalingAdapter,
    private val audioConfig: AudioPushConfig,
    private val retryConfig: RetryConfig
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    actual val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow<WebRTCStats?>(null)
    actual val stats: StateFlow<WebRTCStats?> = _stats.asStateFlow()

    actual suspend fun connect() {
        _state.value = SessionState.Error(
            message = "WasmJS WhipSession not yet fully implemented",
            isRetryable = false
        )
    }

    actual fun createDataChannel(config: DataChannelConfig): DataChannel? = null

    actual fun setMuted(muted: Boolean) {}

    actual fun toggleMute() {}

    actual fun close() {
        _state.value = SessionState.Closed
    }
}
