package com.syncrobotic.webrtc.session

/**
 * Connection state of [WebRTCSession], exposed as `StateFlow<SessionState>`.
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

    /**
     * Connection lost, attempting reconnection.
     * @param attempt Current retry attempt number (1-indexed)
     * @param maxAttempts Maximum number of retry attempts, or `null` for unlimited retries
     */
    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int?
    ) : SessionState()

    /**
     * Connection error. Check [isRetryable] for recovery possibility.
     *
     * @param message Technical detail for logs and crash reports. **Not UI copy** —
     *   it may carry an HTTP response body or internal server text. Render
     *   [userMessage] / [userHint] instead, or switch on [kind] for localized copy.
     * @param cause Underlying throwable, if any
     * @param isRetryable Whether reconnecting has a realistic chance of succeeding
     * @param kind Semantic category used to derive user-facing copy
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val isRetryable: Boolean = true,
        val kind: SessionErrorKind = SessionErrorKind.UNKNOWN
    ) : SessionState() {
        /** Short user-facing headline, safe to display. Derived from [kind]. */
        val userMessage: String get() = kind.userMessage

        /** One-line user-facing hint, safe to display. Derived from [kind]. */
        val userHint: String get() = kind.userHint
    }

    /** Session closed. Terminal state — create a new session to reconnect. */
    data object Closed : SessionState()
}
