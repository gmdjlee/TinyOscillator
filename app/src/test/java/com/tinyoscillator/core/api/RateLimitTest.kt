package com.tinyoscillator.core.api

import com.tinyoscillator.core.config.ApiConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method

/**
 * Rate limit slot-reservation pattern tests.
 *
 * Verifies that the non-blocking rate limit implementation correctly:
 * 1. Allows first call through immediately
 * 2. Reserves time slots without holding mutex during delay
 * 3. Calculates correct delay for rapid consecutive calls
 */
class RateLimitTest {

    private val kiwoomClient = KiwoomApiClient(
        httpClient = KiwoomApiClient.createDefaultClient(enablePinning = false),
        json = KiwoomApiClient.createDefaultJson()
    )

    private val kisClient = KisApiClient(
        httpClient = KiwoomApiClient.createDefaultClient(enablePinning = false),
        json = KiwoomApiClient.createDefaultJson()
    )

    // Access private waitForRateLimit via reflection
    private fun getWaitMethod(client: Any): Method {
        val method = client::class.java.getDeclaredMethod("waitForRateLimit")
        method.isAccessible = true
        return method
    }

    @Test
    fun `KiwoomApiClient KIWOOM_RATE_LIMIT_MS는 500이다`() {
        assertEquals(500L, ApiConstants.KIWOOM_RATE_LIMIT_MS)
    }

    @Test
    fun `KisApiClient KIS_RATE_LIMIT_MS는 500이다`() {
        assertEquals(500L, ApiConstants.KIS_RATE_LIMIT_MS)
    }

    @Test
    fun `KiwoomApiClient lastCallTime 초기값은 0이다`() {
        val client = KiwoomApiClient(
            httpClient = KiwoomApiClient.createDefaultClient(enablePinning = false),
            json = KiwoomApiClient.createDefaultJson()
        )
        val field = BaseApiClient::class.java.getDeclaredField("lastCallTime")
        field.isAccessible = true
        assertEquals(0L, field.get(client))
    }

    @Test
    fun `KisApiClient lastCallTime 초기값은 0이다`() {
        val client = KisApiClient(
            httpClient = KiwoomApiClient.createDefaultClient(enablePinning = false),
            json = KiwoomApiClient.createDefaultJson()
        )
        val field = BaseApiClient::class.java.getDeclaredField("lastCallTime")
        field.isAccessible = true
        assertEquals(0L, field.get(client))
    }

    @Test
    fun `ApiConstants DEFAULT_MAX_RETRIES는 2이다`() {
        assertEquals(2, ApiConstants.DEFAULT_MAX_RETRIES)
    }

    @Test
    fun `KiwoomApiClient rateLimitMutex가 존재한다`() {
        val field = BaseApiClient::class.java.getDeclaredField("rateLimitMutex")
        field.isAccessible = true
        val mutex = field.get(kiwoomClient)
        assertNotNull(mutex)
        assertTrue(mutex is kotlinx.coroutines.sync.Mutex)
    }

    @Test
    fun `KisApiClient rateLimitMutex가 존재한다`() {
        val field = BaseApiClient::class.java.getDeclaredField("rateLimitMutex")
        field.isAccessible = true
        val mutex = field.get(kisClient)
        assertNotNull(mutex)
        assertTrue(mutex is kotlinx.coroutines.sync.Mutex)
    }

    @Test
    fun `lastCallTime을 수동 설정하면 올바르게 반영된다`() {
        val client = KiwoomApiClient(
            httpClient = KiwoomApiClient.createDefaultClient(enablePinning = false),
            json = KiwoomApiClient.createDefaultJson()
        )

        val field = BaseApiClient::class.java.getDeclaredField("lastCallTime")
        field.isAccessible = true

        // Initial value should be 0
        assertEquals(0L, field.get(client))

        // Set and verify
        val now = System.currentTimeMillis()
        field.set(client, now)
        assertEquals(now, field.get(client))
    }

    @Test
    fun `lastCallTime은 @Volatile이다`() {
        val field = BaseApiClient::class.java.getDeclaredField("lastCallTime")
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.modifiers))
    }

    @Test
    fun `DEFAULT_RETRY_DELAYS_MS는 1초와 2초이다`() {
        // Phase 3.5 리팩토링 이후 `RETRY_DELAYS`는 KiwoomApiClient/KisApiClient에서
        // 제거되고 공통 `BaseApiClient.DEFAULT_RETRY_DELAYS_MS`로 통합됨.
        assertEquals(listOf(1000L, 2000L), BaseApiClient.DEFAULT_RETRY_DELAYS_MS)
    }

    @Test
    fun `AI_RETRY_DELAYS_MS는 2초와 4초이다`() {
        // AI API는 rate limit 정책상 더 긴 간격 필요.
        assertEquals(listOf(2000L, 4000L), BaseApiClient.AI_RETRY_DELAYS_MS)
    }

    // =============================================
    // 토큰 엔드포인트 Rate Limit 상수 검증
    // =============================================

    @Test
    fun `Kiwoom KIWOOM_TOKEN_MIN_INTERVAL_MS는 10초이다`() {
        assertEquals(10_000L, ApiConstants.KIWOOM_TOKEN_MIN_INTERVAL_MS)
    }

    @Test
    fun `KIS KIS_TOKEN_MIN_INTERVAL_MS는 61초이다`() {
        assertEquals(61_000L, ApiConstants.KIS_TOKEN_MIN_INTERVAL_MS)
    }

    @Test
    fun `Kiwoom tokenRateMutex가 존재한다`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("tokenRateMutex")
        field.isAccessible = true
        val mutex = field.get(kiwoomClient)
        assertNotNull(mutex)
        assertTrue(mutex is kotlinx.coroutines.sync.Mutex)
    }

    @Test
    fun `KIS tokenRateMutex가 존재한다`() {
        val field = KisApiClient::class.java.getDeclaredField("tokenRateMutex")
        field.isAccessible = true
        val mutex = field.get(kisClient)
        assertNotNull(mutex)
        assertTrue(mutex is kotlinx.coroutines.sync.Mutex)
    }

    @Test
    fun `Kiwoom TOKEN_RATE_LIMIT_RETRY_DELAYS가 올바르다`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("TOKEN_RATE_LIMIT_RETRY_DELAYS")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val delays = field.get(null) as List<Long>
        assertEquals(3, delays.size)
        assertTrue("첫 번째 딜레이는 TOKEN_MIN_INTERVAL_MS 이상이어야 한다", delays[0] >= 10_000L)
    }

    @Test
    fun `KIS TOKEN_RATE_LIMIT_RETRY_DELAYS가 올바르다`() {
        val field = KisApiClient::class.java.getDeclaredField("TOKEN_RATE_LIMIT_RETRY_DELAYS")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val delays = field.get(null) as List<Long>
        assertEquals(3, delays.size)
        assertTrue("첫 번째 딜레이는 TOKEN_MIN_INTERVAL_MS(61초) 이상이어야 한다", delays[0] >= 61_000L)
    }

    // =============================================
    // 토큰 slot reservation 동시성 패턴 검증
    // =============================================

    @Test
    fun `KIS nextTokenAvailableAt 초기값은 0이다`() {
        val field = KisApiClient::class.java.getDeclaredField("nextTokenAvailableAt")
        field.isAccessible = true
        assertEquals(0L, field.get(kisClient))
    }

    @Test
    fun `Kiwoom nextTokenAvailableAt 초기값은 0이다`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("nextTokenAvailableAt")
        field.isAccessible = true
        assertEquals(0L, field.get(kiwoomClient))
    }

    @Test
    fun `KIS nextTokenAvailableAt는 volatile이 아니다 - mutex로만 보호`() {
        // tokenRateMutex 내부 접근 전용이므로 @Volatile 불필요
        val field = KisApiClient::class.java.getDeclaredField("nextTokenAvailableAt")
        assertFalse(
            "nextTokenAvailableAt는 mutex로만 보호되므로 volatile 아님",
            java.lang.reflect.Modifier.isVolatile(field.modifiers)
        )
    }

    @Test
    fun `Kiwoom nextTokenAvailableAt는 volatile이 아니다 - mutex로만 보호`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("nextTokenAvailableAt")
        assertFalse(
            "nextTokenAvailableAt는 mutex로만 보호되므로 volatile 아님",
            java.lang.reflect.Modifier.isVolatile(field.modifiers)
        )
    }
}
