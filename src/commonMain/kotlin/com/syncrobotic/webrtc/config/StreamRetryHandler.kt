package com.syncrobotic.webrtc.config

import com.syncrobotic.webrtc.signaling.SignalingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Utility for retrying stream connections with exponential backoff.
 */
object StreamRetryHandler {

    /**
     * Execute [block] with automatic retry on failure.
     *
     * @param config         Retry configuration (max retries, backoff, etc.)
     * @param actionName     Human-readable name for logging
     * @param onAttempt      Called before each retry (not the first attempt)
     * @param onRetryError   Called when an individual retry attempt fails
     * @param block          The suspending operation to attempt
     * @return The result of [block] on success
     * @throws Exception     The last exception if all retries are exhausted
     */
    suspend fun <T> withRetry(
        config: RetryConfig,
        actionName: String = "connection",
        onAttempt: (attempt: Int, maxAttempts: Int?, delayMs: Long) -> Unit = { _, _, _ -> },
        onRetryError: (attempt: Int, error: Throwable) -> Unit = { _, _ -> },
        block: suspend (attempt: Int) -> T
    ): T {
        val isUnlimited = config.maxRetries == Int.MAX_VALUE
        val maxAttempts = if (isUnlimited) Int.MAX_VALUE else config.maxRetries + 1
        var lastException: Throwable? = null

        println("${WebRtcLog.ts()} [StreamRetryHandler] [$actionName] Starting with maxRetries=${if (isUnlimited) "unlimited" else config.maxRetries}, initialDelay=${config.initialDelayMs}ms, backoff=${config.backoffFactor}")

        var attempt = 0
        while (true) {
            attempt++
            // Guard: for non-unlimited, stop after maxAttempts
            if (!isUnlimited && attempt > maxAttempts) break

            try {
                println("${WebRtcLog.ts()} [StreamRetryHandler] [$actionName] Attempt #$attempt")
                return block(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastException = e
                println("${WebRtcLog.ts()} [StreamRetryHandler] [$actionName] Attempt #$attempt failed: ${e::class.simpleName}: ${e.message}")

                if (!shouldRetry(e, config)) {
                    println("${WebRtcLog.ts()} [StreamRetryHandler] [$actionName] Error is not retryable, giving up")
                    throw e
                }

                if (!isUnlimited && attempt >= maxAttempts) {
                    println("${WebRtcLog.ts()} [StreamRetryHandler] [$actionName] All $maxAttempts attempts exhausted")
                    break
                }

                val delayMs = config.calculateDelay(attempt - 1)
                val displayMaxRetries = if (isUnlimited) "unlimited" else "${maxAttempts - 1}"
                println("${WebRtcLog.ts()} [StreamRetryHandler] [$actionName] Retrying in ${delayMs}ms (attempt $attempt/$displayMaxRetries)")
                onAttempt(attempt, if (isUnlimited) null else maxAttempts - 1, delayMs)
                onRetryError(attempt, e)
                delay(delayMs)
            }
        }

        val retriesDisplay = if (isUnlimited) "unlimited" else "${config.maxRetries}"
        throw StreamRetryExhaustedException(
            message = "$actionName failed after $retriesDisplay retries",
            cause = lastException,
            totalAttempts = attempt
        )
    }

    /**
     * Determine whether the given error is retryable.
     */
    fun shouldRetry(error: Throwable, config: RetryConfig): Boolean {
        if (!config.retryOnError) return false

        return when (error) {
            is IllegalStateException -> false
            is IllegalArgumentException -> false
            is UnsupportedOperationException -> false
            is NotImplementedError -> false
            is SignalingException -> isRetryableSignaling(error)
            is StreamRetryExhaustedException -> false
            else -> true
        }
    }

    /** 4xx statuses worth retrying despite being client errors (transient / eventually-available). */
    private val RETRYABLE_4XX = setOf(404, 408, 425, 429)

    /**
     * A signaling failure is retryable unless the server permanently rejected the request.
     * 4xx (e.g. 406 unsupported media, 400 bad offer) will keep failing on identical retries,
     * so we give up immediately instead of hammering the device. 404/408/425/429 and 5xx
     * are treated as transient. A null status means a network/transport error → retry.
     */
    private fun isRetryableSignaling(error: SignalingException): Boolean {
        val status = error.httpStatus ?: return true
        if (status in 400..499 && status !in RETRYABLE_4XX) return false
        return true
    }
}

/**
 * Thrown when all retry attempts have been exhausted.
 */
class StreamRetryExhaustedException(
    message: String,
    cause: Throwable? = null,
    val totalAttempts: Int = 0
) : Exception(message, cause)
