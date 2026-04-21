package com.tinyoscillator.core.config

/**
 * 외부 API 클라이언트 공통 상수.
 *
 * 개별 `ApiClient` 내부 `companion object`에 산재되어 있던 숫자 리터럴을
 * 한 곳에서 관리한다. 재시도 횟수/레이트리밋/토큰 재발급 간격은 각 공급자
 * 정책에 따라 값이 다르므로 제공자별 네임스페이스로 구분한다.
 */
object ApiConstants {

    /** 네트워크 실패 시 기본 재시도 횟수 (API Client 공통) */
    const val DEFAULT_MAX_RETRIES = 2

    // ---------------------------------------------------------------
    // 레이트 리밋 (요청 간 최소 대기 시간)
    // ---------------------------------------------------------------

    /** KIS OpenAPI 호출 간 최소 간격 */
    const val KIS_RATE_LIMIT_MS = 500L

    /** Kiwoom OpenAPI 호출 간 최소 간격 */
    const val KIWOOM_RATE_LIMIT_MS = 500L

    /** Claude API 호출 간 최소 간격 */
    const val CLAUDE_RATE_LIMIT_MS = 1_000L

    /** Gemini API 호출 간 최소 간격 (Free tier: 5 RPM → 12s 간격) */
    const val GEMINI_RATE_LIMIT_MS = 12_000L

    /** DART OpenAPI 호출 간 최소 간격 */
    const val DART_RATE_LIMIT_MS = 1_000L

    /** BOK ECOS API 호출 간 최소 간격 */
    const val BOK_ECOS_RATE_LIMIT_MS = 1_000L

    // ---------------------------------------------------------------
    // 토큰 재발급 간격 (OAuth2 토큰 발급 API 제한)
    // ---------------------------------------------------------------

    /** KIS: 토큰 재발급 1분당 1회 제한 → 61초 */
    const val KIS_TOKEN_MIN_INTERVAL_MS = 61_000L

    /** Kiwoom: 토큰 발급 최소 간격 10초 */
    const val KIWOOM_TOKEN_MIN_INTERVAL_MS = 10_000L

    // ---------------------------------------------------------------
    // Scraper (HTML) 타임아웃 — 대상 사이트 응답 특성에 맞춰 다르게 설정
    // ---------------------------------------------------------------

    /** 네이버 금융 스크래퍼: 단일 HTML 요청 기본 15초. */
    const val NAVER_SCRAPER_TIMEOUT_SECONDS = 15L

    /** FnGuide 스크래퍼: 단일 HTML 요청 기본 15초. */
    const val FNGUIDE_SCRAPER_TIMEOUT_SECONDS = 15L

    /**
     * Equity 스크래퍼: 페이지당 HTML 크기가 커서 더 긴 타임아웃 필요.
     * 15초로 낮추면 일부 페이지에서 타임아웃 발생 사례 확인됨.
     */
    const val EQUITY_SCRAPER_TIMEOUT_SECONDS = 20L
}
