package com.syncrobotic.webrtc.audio

import com.syncrobotic.webrtc.WebRTCStats
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import com.syncrobotic.webrtc.session.WhipSession
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
 * An [AudioPushController] backed by a [WhipSession].
 */
internal class SessionAudioPushController(
    private val session: WhipSession,
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
