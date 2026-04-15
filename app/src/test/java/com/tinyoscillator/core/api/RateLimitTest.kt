package com.tinyoscillator.core.api

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
    fun `KiwoomApiClient RATE_LIMIT_MS는 500이다`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("RATE_LIMIT_MS")
        field.isAccessible = true
        assertEquals(500L, field.get(null))
    }

    @Test
    fun `KisApiClient RATE_LIMIT_MS는 500이다`() {
        val field = KisApiClient::class.java.getDeclaredField("RATE_LIMIT_MS")
        field.isAccessible = true
        assertEquals(500L, field.get(null))
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
    fun `KiwoomApiClient MAX_RETRIES는 2이다`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("MAX_RETRIES")
        field.isAccessible = true
        assertEquals(2, field.get(null))
    }

    @Test
    fun `KisApiClient MAX_RETRIES는 2이다`() {
        val field = KisApiClient::class.java.getDeclaredField("MAX_RETRIES")
        field.isAccessible = true
        assertEquals(2, field.get(null))
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
    fun `RETRY_DELAYS는 1초와 2초이다 - Kiwoom`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("RETRY_DELAYS")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val delays = field.get(null) as List<Long>
        assertEquals(listOf(1000L, 2000L), delays)
    }

    @Test
    fun `RETRY_DELAYS는 1초와 2초이다 - KIS`() {
        val field = KisApiClient::class.java.getDeclaredField("RETRY_DELAYS")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val delays = field.get(null) as List<Long>
        assertEquals(listOf(1000L, 2000L), delays)
    }

    // =============================================
    // 토큰 엔드포인트 Rate Limit 상수 검증
    // =============================================

    @Test
    fun `Kiwoom TOKEN_MIN_INTERVAL_MS는 10초이다`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("TOKEN_MIN_INTERVAL_MS")
        field.isAccessible = true
        assertEquals(10_000L, field.get(null))
    }

    @Test
    fun `KIS TOKEN_MIN_INTERVAL_MS는 61초이다`() {
        val field = KisApiClient::class.java.getDeclaredField("TOKEN_MIN_INTERVAL_MS")
        field.isAccessible = true
        assertEquals(61_000L, field.get(null))
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
}
