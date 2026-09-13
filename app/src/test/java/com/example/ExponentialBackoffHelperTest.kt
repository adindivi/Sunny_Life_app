package com.example

import com.example.data.sync.ExponentialBackoffHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ExponentialBackoffHelperTest {

    @Test
    fun calculateDelayMs_withoutJitter_followsExponentialCurve() {
        // Attempt 0 or negative should return 0
        assertEquals(0L, ExponentialBackoffHelper.calculateDelayMs(0, withJitter = false))
        assertEquals(0L, ExponentialBackoffHelper.calculateDelayMs(-1, withJitter = false))

        // Attempt 1: 1000 * 2^0 = 1000ms
        assertEquals(1000L, ExponentialBackoffHelper.calculateDelayMs(1, withJitter = false))

        // Attempt 2: 1000 * 2^1 = 2000ms
        assertEquals(2000L, ExponentialBackoffHelper.calculateDelayMs(2, withJitter = false))

        // Attempt 3: 1000 * 2^2 = 4000ms
        assertEquals(4000L, ExponentialBackoffHelper.calculateDelayMs(3, withJitter = false))

        // Attempt 4: 1000 * 2^3 = 8000ms
        assertEquals(8000L, ExponentialBackoffHelper.calculateDelayMs(4, withJitter = false))

        // Attempt 5: 1000 * 2^4 = 16000ms
        assertEquals(16000L, ExponentialBackoffHelper.calculateDelayMs(5, withJitter = false))

        // Attempt 6: 1000 * 2^5 = 32000ms (Max delay)
        assertEquals(32000L, ExponentialBackoffHelper.calculateDelayMs(6, withJitter = false))

        // Attempt 10: Capped at maxDelayMs (32000ms)
        assertEquals(32000L, ExponentialBackoffHelper.calculateDelayMs(10, withJitter = false))

        // Attempt 50: Handles high attempt count without numeric overflow
        assertEquals(32000L, ExponentialBackoffHelper.calculateDelayMs(50, withJitter = false))
    }

    @Test
    fun calculateDelayMs_withJitter_isWithinExpectedRange() {
        for (attempt in 1..6) {
            val deterministicDelay = ExponentialBackoffHelper.calculateDelayMs(attempt, withJitter = false)
            val minBound = deterministicDelay / 2
            val maxBound = deterministicDelay

            // Verify with 20 random samples per attempt
            for (sample in 1..20) {
                val jitterDelay = ExponentialBackoffHelper.calculateDelayMs(attempt, withJitter = true)
                assertTrue(
                    "Jitter delay $jitterDelay must be >= $minBound and <= $maxBound for attempt $attempt",
                    jitterDelay in minBound..maxBound
                )
            }
        }
    }

    @Test
    fun retryWithExponentialBackoff_succeedsImmediately() = runBlocking {
        var callCount = 0
        val result = ExponentialBackoffHelper.retryWithExponentialBackoff(
            maxAttempts = 3,
            initialDelayMs = 10L,
            maxDelayMs = 50L
        ) { attempt ->
            callCount++
            "SUCCESS"
        }

        assertTrue(result.isSuccess)
        assertEquals("SUCCESS", result.getOrNull())
        assertEquals(1, callCount)
    }

    @Test
    fun retryWithExponentialBackoff_recoversAfterTransientFailures() = runBlocking {
        var attempts = 0
        val retryLog = mutableListOf<Int>()

        val result = ExponentialBackoffHelper.retryWithExponentialBackoff(
            maxAttempts = 5,
            initialDelayMs = 10L,
            maxDelayMs = 50L,
            onRetry = { attempt, error, nextDelayMs ->
                retryLog.add(attempt)
            }
        ) { attempt ->
            attempts++
            if (attempts < 3) {
                throw IOException("Temporary Network Timeout on attempt $attempt")
            }
            "RECOVERED_DATA"
        }

        assertTrue(result.isSuccess)
        assertEquals("RECOVERED_DATA", result.getOrNull())
        assertEquals(3, attempts)
        assertEquals(listOf(1, 2), retryLog)
    }

    @Test
    fun retryWithExponentialBackoff_failsWhenExceedingMaxAttempts() = runBlocking {
        var attempts = 0
        val result = ExponentialBackoffHelper.retryWithExponentialBackoff(
            maxAttempts = 4,
            initialDelayMs = 5L,
            maxDelayMs = 20L
        ) { attempt ->
            attempts++
            throw IllegalStateException("Persistent server 500 error on attempt $attempt")
        }

        assertTrue(result.isFailure)
        assertEquals(4, attempts)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals("Persistent server 500 error on attempt 4", result.exceptionOrNull()?.message)
    }
}
