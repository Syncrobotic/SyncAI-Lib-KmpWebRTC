package com.syncrobotic.webrtc.config

import com.syncrobotic.webrtc.signaling.WebSocketSignalingException
import com.syncrobotic.webrtc.signaling.WhepException
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
        onAttempt: (attempt: Int, maxAttempts: Int, delayMs: Long) -> Unit = { _, _, _ -> },
        onRetryError: (attempt: Int, error: Throwable) -> Unit = { _, _ -> },
        block: suspend (attempt: Int) -> T
    ): T {
        val maxAttempts = config.maxRetries + 1
        var lastException: Throwable? = null

        for (attempt in 1..maxAttempts) {
            try {
                return block(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastException = e

                if (!shouldRetry(e, config)) {
                    throw e
                }

                if (attempt >= maxAttempts) {
                    break
                }

                val delayMs = config.calculateDelay(attempt - 1)
                onAttempt(attempt, maxAttempts - 1, delayMs)
                onRetryError(attempt, e)
                delay(delayMs)
            }
        }

        throw StreamRetryExhaustedException(
            message = "$actionName failed after ${config.maxRetries} retries",
            cause = lastException,
            totalAttempts = maxAttempts
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
            is WhepException -> true
            is WebSocketSignalingException -> true
            is StreamRetryExhaustedException -> false
            else -> true
        }
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
