package com.tinyoscillator.core.api

import org.junit.Assert.*
import org.junit.Test

/**
 * ApiModels 단위 테스트
 *
 * KiwoomApiKeyConfig, KisApiKeyConfig의 isValid/getBaseUrl,
 * TokenInfo의 isExpired/bearer 검증
 */
class ApiModelsTest {

    // ========== KiwoomApiKeyConfig.isValid 테스트 ==========

    @Test
    fun `KiwoomApiKeyConfig - 유효한 키`() {
        val config = KiwoomApiKeyConfig(appKey = "myAppKey", secretKey = "mySecret")
        assertTrue(config.isValid())
    }

    @Test
    fun `KiwoomApiKeyConfig - appKey가 빈 문자열이면 invalid`() {
        val config = KiwoomApiKeyConfig(appKey = "", secretKey = "mySecret")
        assertFalse(config.isValid())
    }

    @Test
    fun `KiwoomApiKeyConfig - secretKey가 빈 문자열이면 invalid`() {
        val config = KiwoomApiKeyConfig(appKey = "myAppKey", secretKey = "")
        assertFalse(config.isValid())
    }

    @Test
    fun `KiwoomApiKeyConfig - 공백만 있는 키는 invalid`() {
        val config = KiwoomApiKeyConfig(appKey = "   ", secretKey = "mySecret")
        assertFalse(config.isValid())
    }

    // ========== KiwoomApiKeyConfig.getBaseUrl 테스트 ==========

    @Test
    fun `KiwoomApiKeyConfig - MOCK 모드 URL`() {
        val config = KiwoomApiKeyConfig(investmentMode = InvestmentMode.MOCK)
        assertEquals("https://mockapi.kiwoom.com", config.getBaseUrl())
    }

    @Test
    fun `KiwoomApiKeyConfig - PRODUCTION 모드 URL`() {
        val config = KiwoomApiKeyConfig(investmentMode = InvestmentMode.PRODUCTION)
        assertEquals("https://api.kiwoom.com", config.getBaseUrl())
    }

    // ========== KisApiKeyConfig.isValid 테스트 ==========

    @Test
    fun `KisApiKeyConfig - 유효한 키`() {
        val config = KisApiKeyConfig(appKey = "myAppKey", appSecret = "mySecret")
        assertTrue(config.isValid())
    }

    @Test
    fun `KisApiKeyConfig - appKey가 빈 문자열이면 invalid`() {
        val config = KisApiKeyConfig(appKey = "", appSecret = "mySecret")
        assertFalse(config.isValid())
    }

    @Test
    fun `KisApiKeyConfig - appSecret가 빈 문자열이면 invalid`() {
        val config = KisApiKeyConfig(appKey = "myAppKey", appSecret = "")
        assertFalse(config.isValid())
    }

    // ========== KisApiKeyConfig.getBaseUrl 테스트 ==========

    @Test
    fun `KisApiKeyConfig - MOCK 모드 URL`() {
        val config = KisApiKeyConfig(investmentMode = InvestmentMode.MOCK)
        assertEquals("https://openapivts.koreainvestment.com:29443", config.getBaseUrl())
    }

    @Test
    fun `KisApiKeyConfig - PRODUCTION 모드 URL`() {
        val config = KisApiKeyConfig(investmentMode = InvestmentMode.PRODUCTION)
        assertEquals("https://openapi.koreainvestment.com:9443", config.getBaseUrl())
    }

    // ========== TokenInfo.isExpired 테스트 ==========

    @Test
    fun `TokenInfo - 미래 만료 시간이면 만료되지 않음`() {
        val token = TokenInfo(
            token = "test_token",
            expiresAtMillis = System.currentTimeMillis() + 3_600_000 // 1시간 후
        )
        assertFalse(token.isExpired())
    }

    @Test
    fun `TokenInfo - 과거 만료 시간이면 만료됨`() {
        val token = TokenInfo(
            token = "test_token",
            expiresAtMillis = System.currentTimeMillis() - 3_600_000 // 1시간 전
        )
        assertTrue(token.isExpired())
    }

    @Test
    fun `TokenInfo - 1분 이내 만료면 만료 처리 (60초 버퍼)`() {
        val token = TokenInfo(
            token = "test_token",
            expiresAtMillis = System.currentTimeMillis() + 30_000 // 30초 후 (1분 이내)
        )
        assertTrue(token.isExpired()) // 60초 전 만료 처리이므로 true
    }

    @Test
    fun `TokenInfo - 정확히 1분 후 만료면 만료 처리`() {
        // expiresAtMillis - 60_000 == currentTimeMillis 이면 >= 조건이므로 true
        val now = System.currentTimeMillis()
        val token = TokenInfo(
            token = "test_token",
            expiresAtMillis = now + 60_000
        )
        assertTrue(token.isExpired())
    }

    // ========== TokenInfo.bearer 테스트 ==========

    @Test
    fun `TokenInfo - bearer 형식`() {
        val token = TokenInfo(token = "abc123", expiresAtMillis = 0L)
        assertEquals("Bearer abc123", token.bearer)
    }

    @Test
    fun `TokenInfo - 기본 tokenType은 bearer`() {
        val token = TokenInfo(token = "test", expiresAtMillis = 0L)
        assertEquals("bearer", token.tokenType)
    }

    // ========== InvestmentMode 테스트 ==========

    @Test
    fun `InvestmentMode - displayName 검증`() {
        assertEquals("모의투자", InvestmentMode.MOCK.displayName)
        assertEquals("실전투자", InvestmentMode.PRODUCTION.displayName)
    }

    // ========== ApiError 테스트 ==========

    @Test
    fun `ApiError - ApiCallError 메시지 포맷`() {
        val error = ApiError.ApiCallError(404, "Not Found")
        assertEquals("[404] Not Found", error.message)
    }

    @Test
    fun `ApiError - NoApiKeyError 기본 메시지`() {
        val error = ApiError.NoApiKeyError()
        assertEquals("API 키가 설정되지 않았습니다. 설정에서 API 키를 입력해주세요.", error.message)
    }
}
