package com.syncrobotic.webrtc.session

/**
 * Connection state of [WhepSession] / [WhipSession], exposed as `StateFlow<SessionState>`.
 *
 * State transitions:
 * ```
 * Idle → Connecting → Connected ← → Reconnecting
 *                  ↘ Error
 * Any → Closed (terminal)
 * ```
 */
sealed class SessionState {
    /** Session created, not yet connected. */
    data object Idle : SessionState()

    /** Establishing WebRTC connection (SDP/ICE negotiation). */
    data object Connecting : SessionState()

    /** WebRTC connected, media flowing. */
    data object Connected : SessionState()

    /** Connection lost, attempting reconnection. */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int
    ) : SessionState()

    /** Connection error. Check [isRetryable] for recovery possibility. */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRetryable: Boolean = true
    ) : SessionState()

    /** Session closed. Terminal state — create a new session to reconnect. */
    data object Closed : SessionState()
}
