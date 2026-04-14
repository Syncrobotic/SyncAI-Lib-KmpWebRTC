package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession

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
 * A [VideoPlayerController] backed by a [WebRTCSession].
 */
internal class WebRTCSessionVideoPlayerController(
    private val session: WebRTCSession
) : VideoPlayerController {
    override fun play() { session.setAudioEnabled(true) }
    override fun pause() { session.setAudioEnabled(false) }
    override fun stop() { session.close() }
    override fun seekTo(positionMs: Long) {}
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
                            if (sessionState.maxAttempts == null)
                                "Reconnecting (${sessionState.attempt})..."
                            else
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

/**
 * Semi-transparent status overlay shown on top of a video frame
 * when the session is not in Connected state.
 *
 * Shows last frame underneath with a dark overlay + status text.
 */
@Composable
internal fun SessionStatusOverlay(
    sessionState: SessionState,
    modifier: Modifier = Modifier
) {
    // Only show overlay when NOT connected and NOT idle/closed
    val shouldShow = sessionState is SessionState.Connecting
            || sessionState is SessionState.Reconnecting
            || sessionState is SessionState.Error

    if (!shouldShow) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            when (sessionState) {
                is SessionState.Connecting -> {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Connecting...",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                is SessionState.Reconnecting -> {
                    CircularProgressIndicator(color = Color(0xFFFFA500), strokeWidth = 2.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (sessionState.maxAttempts == null)
                            "Reconnecting (${sessionState.attempt})..."
                        else
                            "Reconnecting (${sessionState.attempt}/${sessionState.maxAttempts})...",
                        color = Color(0xFFFFA500),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                is SessionState.Error -> {
                    Text(
                        text = "\u26A0",
                        fontSize = 24.sp,
                        color = Color.Red
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sessionState.message,
                        color = Color.Red,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    if (sessionState.isRetryable) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Will retry automatically",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {}
            }
        }
    }
}
