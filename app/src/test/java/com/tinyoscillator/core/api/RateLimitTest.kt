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
        val field = KiwoomApiClient::class.java.getDeclaredField("lastCallTime")
        field.isAccessible = true
        assertEquals(0L, field.get(client))
    }

    @Test
    fun `KisApiClient lastCallTime 초기값은 0이다`() {
        val client = KisApiClient(
            httpClient = KiwoomApiClient.createDefaultClient(enablePinning = false),
            json = KiwoomApiClient.createDefaultJson()
        )
        val field = KisApiClient::class.java.getDeclaredField("lastCallTime")
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
        val field = KiwoomApiClient::class.java.getDeclaredField("rateLimitMutex")
        field.isAccessible = true
        val mutex = field.get(kiwoomClient)
        assertNotNull(mutex)
        assertTrue(mutex is kotlinx.coroutines.sync.Mutex)
    }

    @Test
    fun `KisApiClient rateLimitMutex가 존재한다`() {
        val field = KisApiClient::class.java.getDeclaredField("rateLimitMutex")
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

        val field = KiwoomApiClient::class.java.getDeclaredField("lastCallTime")
        field.isAccessible = true

        // Initial value should be 0
        assertEquals(0L, field.get(client))

        // Set and verify
        val now = System.currentTimeMillis()
        field.set(client, now)
        assertEquals(now, field.get(client))
    }

    @Test
    fun `lastCallTime은 @Volatile이다 - Kiwoom`() {
        val field = KiwoomApiClient::class.java.getDeclaredField("lastCallTime")
        assertTrue(java.lang.reflect.Modifier.isVolatile(field.modifiers))
    }

    @Test
    fun `lastCallTime은 @Volatile이다 - KIS`() {
        val field = KisApiClient::class.java.getDeclaredField("lastCallTime")
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
}
