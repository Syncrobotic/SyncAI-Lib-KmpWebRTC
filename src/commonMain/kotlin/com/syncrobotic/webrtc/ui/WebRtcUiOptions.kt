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
}
