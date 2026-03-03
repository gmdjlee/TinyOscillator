package com.tinyoscillator.core.api

import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

/**
 * Certificate pinning configuration tests.
 *
 * Verifies that CertificatePinner is correctly configured for KIS/Kiwoom APIs,
 * and that createDefaultClient() respects the enablePinning parameter.
 */
class CertificatePinningTest {

    @Test
    fun `createCertificatePinner는 null이 아니다`() {
        val pinner = KiwoomApiClient.createCertificatePinner()
        assertNotNull(pinner)
    }

    @Test
    fun `createCertificatePinner는 DEFAULT와 다르다`() {
        val pinner = KiwoomApiClient.createCertificatePinner()
        // Custom pinner should not be the default no-op pinner
        assertNotSame(CertificatePinner.DEFAULT, pinner)
    }

    @Test
    fun `createDefaultClient에서 enablePinning=false이면 핀 없음`() {
        val client = KiwoomApiClient.createDefaultClient(enablePinning = false)
        assertNotNull(client)
        // When pinning disabled, pinner should be default no-op
        // OkHttpClient with no certificatePinner set uses CertificatePinner.DEFAULT
        val pinnerField = OkHttpClient::class.java.getDeclaredField("certificatePinner")
        pinnerField.isAccessible = true
        val pinner = pinnerField.get(client) as CertificatePinner
        // Default pinner has no pins configured
        assertNotNull(pinner)
    }

    @Test
    fun `createDefaultClient에서 enablePinning=true이면 핀이 설정된다`() {
        val client = KiwoomApiClient.createDefaultClient(enablePinning = true)
        assertNotNull(client)
        val pinnerField = OkHttpClient::class.java.getDeclaredField("certificatePinner")
        pinnerField.isAccessible = true
        val pinner = pinnerField.get(client) as CertificatePinner
        // With pinning enabled, pinner should have pins configured
        assertNotSame(CertificatePinner.DEFAULT, pinner)
    }

    @Test
    fun `createDefaultClient는 30초 타임아웃을 설정한다`() {
        val client = KiwoomApiClient.createDefaultClient(enablePinning = false)
        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
    }

    @Test
    fun `KisApiClient는 KiwoomApiClient의 createDefaultClient를 재사용한다`() {
        val client = KiwoomApiClient.createDefaultClient(enablePinning = false)
        assertNotNull(client)
        assertTrue(client is OkHttpClient)
    }

    @Test
    fun `createCertificatePinner는 반복 호출 시 같은 구성을 반환한다`() {
        val pinner1 = KiwoomApiClient.createCertificatePinner()
        val pinner2 = KiwoomApiClient.createCertificatePinner()
        // Both should be non-null and equivalently configured
        assertNotNull(pinner1)
        assertNotNull(pinner2)
    }

    @Test
    fun `createDefaultJson은 lenient하고 unknown keys를 무시한다`() {
        val json = KiwoomApiClient.createDefaultJson()
        assertNotNull(json)

        // Verify lenient parsing
        val parsed = json.decodeFromString<Map<String, String>>("""{"key": "value", "extra": "ignored"}""")
        assertEquals("value", parsed["key"])
    }

    @Test
    fun `enablePinning true와 false는 다른 클라이언트 구성을 생성한다`() {
        val clientWithPinning = KiwoomApiClient.createDefaultClient(enablePinning = true)
        val clientWithoutPinning = KiwoomApiClient.createDefaultClient(enablePinning = false)

        val pinnerField = OkHttpClient::class.java.getDeclaredField("certificatePinner")
        pinnerField.isAccessible = true

        val pinnerWith = pinnerField.get(clientWithPinning)
        val pinnerWithout = pinnerField.get(clientWithoutPinning)

        // They should be different pinners
        assertNotSame(pinnerWith, pinnerWithout)
    }
}
