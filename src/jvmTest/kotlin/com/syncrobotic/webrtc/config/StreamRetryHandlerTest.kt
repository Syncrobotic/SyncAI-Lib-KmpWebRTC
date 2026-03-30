package com.syncrobotic.webrtc.config

import com.syncrobotic.webrtc.signaling.WebSocketSignalingException
import com.syncrobotic.webrtc.signaling.WhepException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for StreamRetryHandler.
 * Covers TEST_SPEC: SRH-01 through SRH-14.
 */
class StreamRetryHandlerTest {

    private val config = RetryConfig(
        maxRetries = 3,
        initialDelayMs = 10L, // short for tests
        maxDelayMs = 100L,
        backoffFactor = 2.0,
        retryOnError = true
    )

    @Test
    fun `SRH-01 success on first try`() = runTest {
        var callCount = 0
        val result = StreamRetryHandler.withRetry(config) {
            callCount++
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(1, callCount)
    }

    @Test
    fun `SRH-02 fail once then succeed`() = runTest {
        var callCount = 0
        val result = StreamRetryHandler.withRetry(config) {
            callCount++
            if (callCount == 1) throw WhepException("fail")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `SRH-03 all retries exhausted`() = runTest {
        var callCount = 0
        val ex = assertFailsWith<StreamRetryExhaustedException> {
            StreamRetryHandler.withRetry(config) {
                callCount++
                throw WhepException("always fail")
            }
        }
        assertEquals(config.maxRetries + 1, callCount)
        assertEquals(config.maxRetries + 1, ex.totalAttempts)
    }

    @Test
    fun `SRH-04 CancellationException rethrown immediately`() = runTest {
        var callCount = 0
        assertFailsWith<CancellationException> {
            StreamRetryHandler.withRetry(config) {
                callCount++
                throw CancellationException("cancelled")
            }
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `SRH-05 IllegalStateException not retryable`() = runTest {
        var callCount = 0
        assertFailsWith<IllegalStateException> {
            StreamRetryHandler.withRetry(config) {
                callCount++
                throw IllegalStateException("bad state")
            }
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `SRH-06 IllegalArgumentException not retryable`() = runTest {
        var callCount = 0
        assertFailsWith<IllegalArgumentException> {
            StreamRetryHandler.withRetry(config) {
                callCount++
                throw IllegalArgumentException("bad arg")
            }
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `SRH-07 UnsupportedOperationException not retryable`() = runTest {
        var callCount = 0
        assertFailsWith<UnsupportedOperationException> {
            StreamRetryHandler.withRetry(config) {
                callCount++
                throw UnsupportedOperationException("unsupported")
            }
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `SRH-08 NotImplementedError not retryable`() = runTest {
        var callCount = 0
        assertFailsWith<NotImplementedError> {
            StreamRetryHandler.withRetry(config) {
                callCount++
                throw NotImplementedError("not impl")
            }
        }
        assertEquals(1, callCount)
    }

    @Test
    fun `SRH-09 WhepException is retryable`() = runTest {
        assertTrue(StreamRetryHandler.shouldRetry(WhepException("test"), config))
    }

    @Test
    fun `SRH-10 WebSocketSignalingException is retryable`() = runTest {
        assertTrue(StreamRetryHandler.shouldRetry(WebSocketSignalingException("test"), config))
    }

    @Test
    fun `SRH-11 retryOnError false means no retry`() = runTest {
        val noRetryConfig = config.copy(retryOnError = false)
        assertFalse(StreamRetryHandler.shouldRetry(WhepException("test"), noRetryConfig))
        assertFalse(StreamRetryHandler.shouldRetry(RuntimeException("test"), noRetryConfig))
    }

    @Test
    fun `SRH-12 onAttempt callback called with correct args`() = runTest {
        val attempts = mutableListOf<Triple<Int, Int, Long>>()
        var callCount = 0
        StreamRetryHandler.withRetry(
            config = config,
            onAttempt = { attempt, maxAttempts, delayMs ->
                attempts.add(Triple(attempt, maxAttempts, delayMs))
            }
        ) {
            callCount++
            if (callCount <= 2) throw WhepException("fail")
            "ok"
        }
        assertEquals(2, attempts.size) // 2 retries before success
        assertEquals(1, attempts[0].first) // first retry attempt = 1
        assertEquals(config.maxRetries, attempts[0].second) // maxAttempts
    }

    @Test
    fun `SRH-13 onRetryError callback called with attempt and error`() = runTest {
        val errors = mutableListOf<Pair<Int, Throwable>>()
        var callCount = 0
        StreamRetryHandler.withRetry(
            config = config,
            onRetryError = { attempt, error ->
                errors.add(attempt to error)
            }
        ) {
            callCount++
            if (callCount <= 2) throw WhepException("fail $callCount")
            "ok"
        }
        assertEquals(2, errors.size)
        assertTrue(errors[0].second is WhepException)
    }

    @Test
    fun `SRH-14 StreamRetryExhaustedException is not retryable`() {
        val ex = StreamRetryExhaustedException("exhausted", totalAttempts = 5)
        assertFalse(StreamRetryHandler.shouldRetry(ex, config))
    }
}
