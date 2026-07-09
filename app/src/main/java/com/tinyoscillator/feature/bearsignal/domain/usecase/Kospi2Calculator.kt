package com.tinyoscillator.feature.bearsignal.domain.usecase

/**
 * §3.5 증폭·집중 `kospi2` 입력 계산 — (삼성전자+SK하이닉스 시가총액) / 코스피 전체 시가총액 × 100 (%).
 *
 * TASK.md §4: `kotlin_krx get_market_cap*` 계열로 조회한 코스피 전종목 시가총액에서 산출한다.
 * 순수 산술만 수행 — 안드로이드/IO/외부 라이브러리 의존성 0 (JVM 단위테스트 대상). data 계층이
 * `com.krxkt.model.MarketCap` 리스트를 `Map<String, Long>`(ticker→시가총액)으로 변환해 전달한다.
 */
object Kospi2Calculator {

    const val TICKER_SAMSUNG_ELECTRONICS = "005930"
    const val TICKER_SK_HYNIX = "000660"

    /**
     * @param marketCapByTicker KOSPI 시장 전종목 시가총액 맵 (ticker → 시가총액, 원)
     * @return 코스피 2사 집중 비중(%). null — 데이터가 비었거나, 전체 시총이 0 이하이거나,
     *   삼성전자·SK하이닉스 중 하나라도 목록에 없는 경우(불완전 데이터로 판단해 계산을 포기한다).
     */
    fun compute(marketCapByTicker: Map<String, Long>): Double? {
        if (marketCapByTicker.isEmpty()) return null
        val totalCap = marketCapByTicker.values.sum()
        if (totalCap <= 0L) return null
        val samsung = marketCapByTicker[TICKER_SAMSUNG_ELECTRONICS] ?: return null
        val skHynix = marketCapByTicker[TICKER_SK_HYNIX] ?: return null
        return (samsung + skHynix).toDouble() / totalCap.toDouble() * 100.0
    }
}
