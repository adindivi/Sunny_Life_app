package com.example.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * 지수 백오프(Exponential Backoff with Full Jitter) 재시도 연산 헬퍼
 * 네트워크 불안정이나 순간적인 통신 실패 시 서버 부하 분산(Thundering Herd 방지)과
 * 신뢰성 높은 재시도를 지원합니다.
 */
object ExponentialBackoffHelper {
    const val DEFAULT_INITIAL_DELAY_MS = 1000L // 기본 1초
    const val DEFAULT_MAX_DELAY_MS = 32000L    // 최대 32초 상한선
    const val DEFAULT_FACTOR = 2.0             // 2배수 지수 증가
    const val DEFAULT_MAX_ATTEMPTS = 5         // 최대 5회 시도

    /**
     * 지정된 재시도 회차에 대한 지수 백오프 대기 시간(ms)을 계산합니다.
     * Full Jitter(50% ~ 100% 무작위 지터)를 적용하여 동일 시각 재시도 집중 현상을 차단합니다.
     *
     * @param attempt 1부터 시작하는 시도 회차 (1회차 실패 후 재시도 = attempt 1)
     * @param initialDelayMs 초기 지연 시간 (기본 1000ms)
     * @param maxDelayMs 최대 지연 상한선 (기본 32000ms)
     * @param factor 지수 증가 승수 (기본 2.0)
     * @param withJitter 지터 난수 적용 여부 (기본 true, 테스트용 false 가능)
     */
    fun calculateDelayMs(
        attempt: Int,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        factor: Double = DEFAULT_FACTOR,
        withJitter: Boolean = true
    ): Long {
        if (attempt <= 0) return 0L

        // 지수 증가 계산 (오버플로우 방지)
        val exp = (attempt - 1).coerceIn(0, 30)
        val calculatedDelay = (initialDelayMs * factor.pow(exp.toDouble())).toLong()
        val cappedDelay = min(maxDelayMs, calculatedDelay)

        return if (withJitter) {
            val minJitter = cappedDelay / 2
            if (minJitter >= cappedDelay) cappedDelay
            else Random.nextLong(minJitter, cappedDelay + 1)
        } else {
            cappedDelay
        }
    }

    /**
     * 서스펜드 람다를 실행하며, 실패 시 지수 백오프 간격으로 지정 횟수만큼 재시도합니다.
     * Coroutine CancellationException은 즉시 전파하여 코루틴 취소 메커니즘을 준수합니다.
     */
    suspend fun <T> retryWithExponentialBackoff(
        maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
        initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
        maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
        factor: Double = DEFAULT_FACTOR,
        withJitter: Boolean = true,
        onRetry: (attempt: Int, error: Throwable, nextDelayMs: Long) -> Unit = { _, _, _ -> },
        block: suspend (attempt: Int) -> T
    ): Result<T> {
        var currentAttempt = 1
        while (true) {
            try {
                return Result.success(block(currentAttempt))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (currentAttempt >= maxAttempts) {
                    return Result.failure(e)
                }
                val delayMs = calculateDelayMs(
                    attempt = currentAttempt,
                    initialDelayMs = initialDelayMs,
                    maxDelayMs = maxDelayMs,
                    factor = factor,
                    withJitter = withJitter
                )
                onRetry(currentAttempt, e, delayMs)
                delay(delayMs)
                currentAttempt++
            }
        }
    }
}
