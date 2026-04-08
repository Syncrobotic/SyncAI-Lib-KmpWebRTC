package com.syncrobotic.webrtc.audio

import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Maps [SessionState] to [AudioPushState].
 */
internal fun SessionState.toAudioPushState(): AudioPushState = when (this) {
    SessionState.Idle -> AudioPushState.Idle
    SessionState.Connecting -> AudioPushState.Connecting
    SessionState.Connected -> AudioPushState.Streaming
    is SessionState.Reconnecting -> AudioPushState.Reconnecting(
        attempt = attempt,
        maxAttempts = maxAttempts
    )
    is SessionState.Error -> AudioPushState.Error(message = message, cause = cause)
    SessionState.Closed -> AudioPushState.Disconnected
}

/**
 * An [AudioPushController] backed by a [WebRTCSession].
 */
internal class WebRTCSessionAudioPushController(
    private val session: WebRTCSession,
    private val scope: CoroutineScope
) : AudioPushController {
    override val state: AudioPushState
        get() = session.state.value.toAudioPushState()

    override val stats: WebRTCStats?
        get() = session.stats.value

    override fun start() {
        scope.launch { session.connect() }
    }

    override fun stop() {
        session.close()
    }

    override fun setMuted(muted: Boolean) {
        session.setMuted(muted)
    }

    override suspend fun refreshStats() {
        // Stats are continuously collected by the session
    }
}

/**
 * Maps [SessionState] to [AudioPlaybackState].
 */
internal fun SessionState.toAudioPlaybackState(audioEnabled: Boolean = true): AudioPlaybackState = when (this) {
    SessionState.Idle -> AudioPlaybackState.Idle
    SessionState.Connecting -> AudioPlaybackState.Connecting
    SessionState.Connected -> if (audioEnabled) AudioPlaybackState.Playing else AudioPlaybackState.Muted
    is SessionState.Reconnecting -> AudioPlaybackState.Reconnecting(attempt = attempt, maxAttempts = maxAttempts)
    is SessionState.Error -> AudioPlaybackState.Error(message = message, cause = cause)
    SessionState.Closed -> AudioPlaybackState.Disconnected
}

/**
 * An [AudioPlayerController] backed by a [WebRTCSession].
 */
internal class WebRTCSessionAudioPlayerController(
    private val session: WebRTCSession,
    private val scope: CoroutineScope
) : AudioPlayerController {
    private var _audioEnabled = true

    override val state: AudioPlaybackState
        get() = session.state.value.toAudioPlaybackState(_audioEnabled)

    override val isPlaying: Boolean
        get() = session.state.value == SessionState.Connected && _audioEnabled

    override val isAudioEnabled: Boolean
        get() = _audioEnabled

    override fun setAudioEnabled(enabled: Boolean) {
        _audioEnabled = enabled
        session.setAudioEnabled(enabled)
    }

    override fun setSpeakerphoneEnabled(enabled: Boolean) {
        session.setSpeakerphoneEnabled(enabled)
    }

    override fun stop() {
        session.close()
    }
}
