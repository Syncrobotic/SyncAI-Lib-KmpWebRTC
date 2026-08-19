package com.syncrobotic.webrtc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.syncrobotic.webrtc.session.SessionState
import com.syncrobotic.webrtc.session.WebRTCSession
import kotlinx.coroutines.delay

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
    is SessionState.Error -> toPlayerError()
    SessionState.Closed -> PlayerState.Stopped
}

/** Narrowing counterpart of [toPlayerState] for the error case. */
internal fun SessionState.Error.toPlayerError(): PlayerState.Error = PlayerState.Error(
    message = message,
    cause = cause,
    kind = kind,
    isRetryable = isRetryable
)

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

// ── Visual tokens ─────────────────────────────────────────────────────
//
// Deliberately desaturated. A stream that can't connect is a status, not an
// alarm the viewer caused — saturated red on a video surface reads as a crash.
// Everything is white-on-dark so it works over any frame content.

private val PrimaryText = Color.White
private val SecondaryText = Color.White.copy(alpha = 0.62f)
private val TertiaryText = Color.White.copy(alpha = 0.40f)
private val IconBackground = Color.White.copy(alpha = 0.10f)
private val OutlineColor = Color.White.copy(alpha = 0.32f)
private val ScrimColor = Color.Black.copy(alpha = 0.55f)
private val CardColor = Color.Black.copy(alpha = 0.45f)

/** Soft amber for "working on it" — distinguishable from the neutral connect spinner. */
private val ReconnectAccent = Color(0xFFE8C27A)

/**
 * How long a reconnect must persist before the overlay covers the last frame.
 *
 * Most reconnects resolve inside a second; flashing a status card at every
 * network hiccup is worse than a briefly frozen frame.
 */
private const val RECONNECT_GRACE_MS = 1500L

private val CardShape = RoundedCornerShape(16.dp)
private val ButtonShape = RoundedCornerShape(18.dp)

/**
 * Placeholder UI shown before the first frame exists — while connecting, or when
 * the session failed before any video arrived.
 */
@Composable
internal fun SessionVideoPlaceholder(
    sessionState: SessionState,
    modifier: Modifier,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // No frame to protect here, so no grace period — show progress immediately.
        when (sessionState) {
            is SessionState.Connecting -> ConnectingContent()
            is SessionState.Reconnecting -> ReconnectingContent(sessionState)
            is SessionState.Error -> ErrorContent(sessionState, onRetry)
            else -> {}
        }
    }
}

/**
 * Status overlay drawn on top of a live video frame when the session leaves
 * [SessionState.Connected].
 *
 * Keeps the last frame visible under a scrim rather than blanking the view.
 */
@Composable
internal fun SessionStatusOverlay(
    sessionState: SessionState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val status = sessionState.toStatusKind()

    // Keyed on the coarse status rather than on sessionState: Reconnecting bumps its
    // attempt counter repeatedly, which would otherwise restart the grace timer and
    // defer the overlay forever.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(status) {
        when (status) {
            OverlayStatus.NONE -> visible = false
            OverlayStatus.RECONNECTING -> {
                delay(RECONNECT_GRACE_MS)
                visible = true
            }
            else -> visible = true
        }
    }

    // The status check also guards the frame between recomposition and the effect
    // running, so returning to Connected doesn't flash a stale card.
    if (!visible || status == OverlayStatus.NONE) return

    Box(
        modifier = modifier.fillMaxSize().background(ScrimColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(CardColor, CardShape)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            when (sessionState) {
                is SessionState.Connecting -> ConnectingContent()
                is SessionState.Reconnecting -> ReconnectingContent(sessionState)
                is SessionState.Error -> ErrorContent(sessionState, onRetry)
                else -> {}
            }
        }
    }
}

/**
 * Renders the status layer for a session, delegating to a consumer-supplied
 * [errorContent] slot when one is provided and the session has errored.
 */
@Composable
internal fun SessionStatusLayer(
    sessionState: SessionState,
    hasVideoFrame: Boolean,
    onRetry: () -> Unit,
    errorContent: (@Composable (PlayerState.Error, () -> Unit) -> Unit)?
) {
    if (sessionState is SessionState.Error && errorContent != null) {
        errorContent(sessionState.toPlayerError(), onRetry)
        return
    }
    if (hasVideoFrame) {
        SessionStatusOverlay(sessionState, onRetry = onRetry)
    } else {
        SessionVideoPlaceholder(sessionState, Modifier, onRetry = onRetry)
    }
}

// ── Internal pieces ───────────────────────────────────────────────────

private enum class OverlayStatus { NONE, CONNECTING, RECONNECTING, ERROR }

private fun SessionState.toStatusKind(): OverlayStatus = when (this) {
    is SessionState.Connecting -> OverlayStatus.CONNECTING
    is SessionState.Reconnecting -> OverlayStatus.RECONNECTING
    is SessionState.Error -> OverlayStatus.ERROR
    else -> OverlayStatus.NONE
}

@Composable
private fun ConnectingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = PrimaryText,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Connecting…",
            color = PrimaryText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Whether a reconnect has gone on long enough to deserve an explanation.
 *
 * Extracted from the composable so the thresholds are unit-testable.
 */
internal fun isProlongedReconnect(attempt: Int, timeThresholdReached: Boolean): Boolean =
    timeThresholdReached || attempt >= WebRtcUiOptions.reconnectHintAfterAttempts

@Composable
private fun ReconnectingContent(state: SessionState.Reconnecting) {
    // Starts when the session begins reconnecting (this content entering composition)
    // and resets if it recovers and drops out again.
    var timeThresholdReached by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(WebRtcUiOptions.reconnectHintAfterMs)
        timeThresholdReached = true
    }
    val prolonged = isProlongedReconnect(state.attempt, timeThresholdReached)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = ReconnectAccent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (prolonged) "Still reconnecting…" else "Reconnecting…",
            color = PrimaryText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        // Under RetryConfig.PERSISTENT the session retries without bound and never
        // reaches Error, so without this the user would watch a bare spinner forever
        // with no idea why. The attempt count is withheld until now on purpose: early
        // on it only churns, but once we're explaining a stall it is real information.
        if (prolonged) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "The device may be offline. Retrying automatically.",
                color = SecondaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Attempt ${state.attempt}",
                color = TertiaryText,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorContent(error: SessionState.Error, onRetry: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(IconBackground, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = PrimaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = error.userMessage,
            color = PrimaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = error.userHint,
            color = SecondaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        if (WebRtcUiOptions.showTechnicalDetails) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error.message.take(WebRtcUiOptions.technicalDetailLimit),
                color = TertiaryText,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
        if (onRetry != null && error.isRetryable) {
            Spacer(Modifier.height(16.dp))
            RetryButton(onRetry)
        }
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(ButtonShape)
            .clickable(onClick = onRetry)
            .border(1.dp, OutlineColor, ButtonShape)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Retry",
            color = PrimaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
