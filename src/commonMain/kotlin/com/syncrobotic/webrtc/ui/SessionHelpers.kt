package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WhepSession

/**
 * Maps [SessionState] to [PlayerState].
 */
internal fun SessionState.toPlayerState(): PlayerState = when (this) {
    SessionState.Idle -> PlayerState.Idle
    SessionState.Connecting -> PlayerState.Connecting
    SessionState.Connected -> PlayerState.Playing
    is SessionState.Reconnecting -> PlayerState.Reconnecting(
        attempt = attempt,
        maxAttempts = maxAttempts
    )
    is SessionState.Error -> PlayerState.Error(message = message, cause = cause)
    SessionState.Closed -> PlayerState.Stopped
}

/**
 * A [VideoPlayerController] backed by a [WhepSession].
 */
internal class SessionVideoPlayerController(
    private val session: WhepSession
) : VideoPlayerController {
    override fun play() {
        session.setAudioEnabled(true)
    }

    override fun pause() {
        session.setAudioEnabled(false)
    }

    override fun stop() {
        session.close()
    }

    override fun seekTo(positionMs: Long) {
        // Not supported for live WebRTC streams
    }

    override val currentPosition: Long get() = 0L
    override val duration: Long get() = 0L
    override val isPlaying: Boolean
        get() = session.state.value == SessionState.Connected
}

/**
 * Placeholder UI shown while the session is connecting or in error state.
 */
@Composable
internal fun SessionVideoPlaceholder(
    sessionState: SessionState,
    modifier: Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (sessionState) {
            is SessionState.Connecting, is SessionState.Reconnecting -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (sessionState is SessionState.Reconnecting)
                            "Reconnecting (${sessionState.attempt}/${sessionState.maxAttempts})..."
                        else
                            "Connecting...",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
            is SessionState.Error -> {
                Text(
                    text = sessionState.message,
                    color = Color.Red,
                    textAlign = TextAlign.Center
                )
            }
            else -> {}
        }
    }
}
