package com.syncrobotic.webrtc.session

import com.syncrobotic.webrtc.signaling.SignalingException

/**
 * Semantic category of a [SessionState.Error].
 *
 * Raw exception text is never safe to show to an end user — it can contain an
 * entire HTTP response body, internal server paths, or stack-trace fragments.
 * [SessionState.Error.message] keeps that text for logs; this enum is what the
 * UI renders, via [userMessage] / [userHint].
 *
 * Consumers that need localized copy should switch on the kind rather than
 * using the built-in English strings:
 *
 * ```kotlin
 * val text = when (error.kind) {
 *     SessionErrorKind.STREAM_UNAVAILABLE -> stringResource(R.string.device_offline)
 *     else -> stringResource(R.string.stream_generic_error)
 * }
 * ```
 */
enum class SessionErrorKind {
    /** Transport failure — host unreachable, DNS failure, TLS error, socket closed. */
    NETWORK,

    /** The request was sent but nothing came back in time. */
    TIMEOUT,

    /** Signaling endpoint rejected the credentials (HTTP 401/403). */
    UNAUTHORIZED,

    /** The endpoint exists but has no stream to hand out (HTTP 404/410) — device likely offline. */
    STREAM_UNAVAILABLE,

    /** Server permanently refused this request (other 4xx) — retrying the same offer won't help. */
    REJECTED,

    /** Signaling server failed on its side (5xx, rate limits). */
    SERVER_ERROR,

    /** Caller/setup mistake — missing Android Context, bad media config, unsupported operation. */
    CONFIGURATION,

    /** Unclassified failure. */
    UNKNOWN;

    /**
     * Whether attempting the connection again has a realistic chance of succeeding.
     *
     * Drives whether the built-in error overlay offers a retry affordance — an
     * expired token or a malformed offer will fail identically every time, so
     * offering "Retry" there is worse than saying nothing.
     */
    val isRetryable: Boolean
        get() = when (this) {
            NETWORK, TIMEOUT, STREAM_UNAVAILABLE, SERVER_ERROR, UNKNOWN -> true
            UNAUTHORIZED, REJECTED, CONFIGURATION -> false
        }

    /** Short user-facing headline. Contains no technical detail. */
    val userMessage: String
        get() = when (this) {
            NETWORK -> "Connection unavailable"
            TIMEOUT -> "Connection timed out"
            UNAUTHORIZED -> "Not authorized"
            STREAM_UNAVAILABLE -> "Stream unavailable"
            REJECTED -> "Can't play this stream"
            SERVER_ERROR -> "Server unavailable"
            CONFIGURATION -> "Can't start video"
            UNKNOWN -> "Video unavailable"
        }

    /** One-line user-facing hint on what to do next. Contains no technical detail. */
    val userHint: String
        get() = when (this) {
            NETWORK -> "Check your network connection and try again."
            TIMEOUT -> "The device took too long to respond."
            UNAUTHORIZED -> "Your session may have expired. Sign in again."
            STREAM_UNAVAILABLE -> "The device may be offline or not streaming."
            REJECTED -> "The server refused this connection request."
            SERVER_ERROR -> "The streaming server is having trouble. Try again shortly."
            CONFIGURATION -> "The video player was not set up correctly."
            UNKNOWN -> "Something went wrong while connecting."
        }
}

/** Depth limit when walking a `cause` chain — guards against pathological nesting. */
private const val CAUSE_CHAIN_LIMIT = 8

/**
 * Classify a connection failure into a [SessionErrorKind] for user-facing display.
 *
 * Walks the `cause` chain because the throwable that surfaces is usually a wrapper
 * ([com.syncrobotic.webrtc.config.StreamRetryExhaustedException] around a
 * [SignalingException] around a platform socket exception).
 */
fun classifySessionError(error: Throwable?): SessionErrorKind {
    if (error == null) return SessionErrorKind.UNKNOWN

    val chain = generateSequence(error) { if (it.cause === it) null else it.cause }
        .take(CAUSE_CHAIN_LIMIT)
        .toList()

    for (e in chain) {
        when (e) {
            is IllegalStateException,
            is IllegalArgumentException,
            is UnsupportedOperationException,
            is NotImplementedError -> return SessionErrorKind.CONFIGURATION

            is SignalingException -> e.httpStatus?.let { return fromHttpStatus(it) }
        }

        // Platform transport exceptions have no common supertype across JVM/Android/iOS,
        // so match on type name and message instead of catching per-platform classes.
        val name = e::class.simpleName ?: ""
        val text = e.message ?: ""
        if (name.contains("Timeout", ignoreCase = true) || text.contains("timed out", ignoreCase = true) ||
            text.contains("timeout", ignoreCase = true)
        ) {
            return SessionErrorKind.TIMEOUT
        }
        if (name.contains("UnknownHost") || name.contains("Connect") || name.contains("Socket") ||
            name.contains("IOException") || name.contains("SSL") || name.contains("Ssl") ||
            name.contains("NSError") || name.contains("Network")
        ) {
            return SessionErrorKind.NETWORK
        }
    }

    // A SignalingException without an HTTP status means the request never got a
    // response at all — that is a transport problem.
    if (chain.any { it is SignalingException }) return SessionErrorKind.NETWORK

    return SessionErrorKind.UNKNOWN
}

/** 4xx statuses that mean "not there yet" rather than "never". Mirrors StreamRetryHandler. */
private fun fromHttpStatus(status: Int): SessionErrorKind = when (status) {
    401, 403 -> SessionErrorKind.UNAUTHORIZED
    404, 410 -> SessionErrorKind.STREAM_UNAVAILABLE
    408 -> SessionErrorKind.TIMEOUT
    425, 429 -> SessionErrorKind.SERVER_ERROR
    in 400..499 -> SessionErrorKind.REJECTED
    in 500..599 -> SessionErrorKind.SERVER_ERROR
    else -> SessionErrorKind.UNKNOWN
}
