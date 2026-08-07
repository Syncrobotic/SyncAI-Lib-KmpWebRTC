package com.syncrobotic.webrtc.config

import kotlin.time.TimeSource

/**
 * Shared monotonic clock for WebRTC critical-path logs.
 *
 * Prefixes each log line with milliseconds since first use so the gaps between
 * steps (e.g. sendOffer → POST response, connect → attempt) are directly
 * diffable without a wall clock.
 */
internal object WebRtcLog {
    private val start = TimeSource.Monotonic.markNow()

    /** e.g. "[+1234ms]" — elapsed since the first WebRTC log line. */
    fun ts(): String = "[+${start.elapsedNow().inWholeMilliseconds}ms]"
}
