package com.syncrobotic.webrtc.config

import kotlin.test.*

/**
 * Unit tests for RetryConfig.
 * Covers TEST_SPEC: RC-01 through RC-09.
 */
class RetryConfigTest {

    @Test
    fun `RC-01 default values`() {
        val config = RetryConfig()
        assertEquals(5, config.maxRetries)
        assertEquals(1000L, config.initialDelayMs)
        assertEquals(45000L, config.maxDelayMs)
        assertEquals(2.0, config.backoffFactor)
        assertEquals(0.1, config.jitterFactor)
    }

    @Test
    fun `RC-02 calculateDelay attempt 0 is around 1000ms`() {
        val config = RetryConfig()
        val delay = config.calculateDelay(0)
        // 1000ms ± 10% jitter → 900..1100
        assertTrue(delay in 900..1100, "Delay was $delay, expected ~1000")
    }

    @Test
    fun `RC-03 calculateDelay attempt 1 is around 2000ms`() {
        val config = RetryConfig()
        val delay = config.calculateDelay(1)
        // 2000ms ± 10% jitter → 1800..2200
        assertTrue(delay in 1800..2200, "Delay was $delay, expected ~2000")
    }

    @Test
    fun `RC-04 calculateDelay attempt 2 is around 4000ms`() {
        val config = RetryConfig()
        val delay = config.calculateDelay(2)
        // 4000ms ± 10% jitter → 3600..4400
        assertTrue(delay in 3600..4400, "Delay was $delay, expected ~4000")
    }

    @Test
    fun `RC-05 calculateDelay large attempt capped at maxDelayMs plus jitter`() {
        val config = RetryConfig()
        val delay = config.calculateDelay(10)
        // Jitter can push delay up to maxDelayMs * (1 + jitterFactor)
        val maxWithJitter = (config.maxDelayMs * (1 + config.jitterFactor)).toLong()
        assertTrue(delay <= maxWithJitter, "Delay $delay exceeded max+jitter $maxWithJitter")
        // Base delay for attempt 10 would be 1000 * 2^10 = 1024000, capped at 45000
        // So delay should be around 45000 ± jitter
        assertTrue(delay >= (config.maxDelayMs * (1 - config.jitterFactor)).toLong(),
            "Delay $delay was too low")
    }

    @Test
    fun `RC-06 AGGRESSIVE preset`() {
        val config = RetryConfig.AGGRESSIVE
        assertEquals(5, config.maxRetries)
        assertEquals(500L, config.initialDelayMs)
        assertEquals(10000L, config.maxDelayMs)
        assertEquals(1.5, config.backoffFactor)
    }

    @Test
    fun `RC-07 DISABLED preset`() {
        val config = RetryConfig.DISABLED
        assertEquals(0, config.maxRetries)
    }

    @Test
    fun `RC-08 delay always non-negative`() {
        val config = RetryConfig(jitterFactor = 1.0) // max jitter
        repeat(100) {
            val delay = config.calculateDelay(0)
            assertTrue(delay >= 0, "Delay was $delay, expected >= 0")
        }
    }

    @Test
    fun `RC-09 data class equality`() {
        val a = RetryConfig(maxRetries = 3, initialDelayMs = 500L)
        val b = RetryConfig(maxRetries = 3, initialDelayMs = 500L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
