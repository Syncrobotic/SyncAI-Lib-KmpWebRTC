package com.syncrobotic.webrtc.ui

/**
 * Global display options for the built-in video overlays.
 *
 * ```kotlin
 * // Application startup, debug builds only
 * WebRtcUiOptions.showTechnicalDetails = BuildConfig.DEBUG
 * ```
 */
object WebRtcUiOptions {
    /**
     * When `true`, the built-in error overlay appends the raw exception text
     * (truncated) beneath the user-facing message.
     *
     * Off by default: the raw text is developer diagnostics, not UI copy.
     * Enable it in debug builds when you need on-device triage without logcat.
     */
    var showTechnicalDetails: Boolean = false

    /** Character cap applied to technical detail text so it can't flood the frame. */
    var technicalDetailLimit: Int = 240

    /**
     * Reconnect attempts after which the overlay explains *why* it is still spinning.
     *
     * With [com.syncrobotic.webrtc.config.RetryConfig.PERSISTENT] the session retries
     * without bound and therefore never reaches [com.syncrobotic.webrtc.session.SessionState.Error],
     * so an offline device would otherwise show a bare spinner indefinitely with no
     * explanation. Past this threshold the overlay adds a reason line — retries continue
     * either way.
     *
     * Whichever of this and [reconnectHintAfterMs] is reached first wins.
     */
    var reconnectHintAfterAttempts: Int = 3

    /** Time spent reconnecting after which the overlay explains why. See [reconnectHintAfterAttempts]. */
    var reconnectHintAfterMs: Long = 15_000L
}
