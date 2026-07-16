package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 도표48 20개 지수 ↔ 자동 수집 소스 매핑표 (TASK.md §4 "해외 19개 지수", §8 리스크).
 *
 * 코스피는 `kotlin_krx get_index_ohlcv("1001")`로 별도 수집(§1.1 [A], 기존 [BearSignalRepositoryImpl]
 * 재사용). 나머지 19개 해외지수는 무인증 시세 소스([GlobalIndexSource] — Yahoo Finance chart API
 * 기본, Stooq CSV 백업)로 커버 가능한 지수만 소스별 티커를 채운다. 선택 소스 실패 시 리포지토리가
 * 나머지 소스로 자동 폴백한다.
 *
 * **커버리지 결정 기준**: Yahoo chart API 실검증(2026-07-17, 지수별 476~511 종가 확인 — 12M 계산
 * 요건 253개 충족)을 통과한 17개 지수를 [MarketCoverage.AUTO]로 채택했다. 이 중 6개 주요 지수
 * (다우·S&P·나스닥·DAX·닛케이·항생)만 Stooq 백업 티커를 함께 유지한다(나머지는 Stooq 심볼 미검증 —
 * 안티봇 차단으로 검증 불가, Yahoo 단독). 베트남(VN지수 Yahoo 미제공)과 RTS(심볼은 있으나 2022 제재
 * 이후 종가 데이터 중단)는 [MarketCoverage.MANUAL_REQUIRED]로 유지한다 — TASK.md §1.1 각주1이
 * 명시적으로 허용하는 폴백이다.
 *
 * [BearSignalReportBaseline.MARKETS]와 이름·순서가 동일하다(20개 지수 SSOT는 그대로 유지, 매핑만 추가).
 */
data class GlobalIndexSpec(
    val name: String,
    val lead: Boolean = false,
    /** Yahoo Finance chart API 심볼 (예: `^DJI`). null이면 해당 소스로 수집 불가 */
    val yahooTicker: String? = null,
    /** Stooq CSV 심볼 (예: `^dji`). null이면 해당 소스로 수집 불가 */
    val stooqTicker: String? = null
) {
    /** 자동 수집 가능 여부 — 소스 중 하나라도 티커가 있으면 true, 전부 null이면 [MarketCoverage.MANUAL_REQUIRED] */
    val autoCovered: Boolean
        get() = GlobalIndexSource.entries.any { tickerFor(it) != null }

    fun tickerFor(source: GlobalIndexSource): String? = when (source) {
        GlobalIndexSource.YAHOO -> yahooTicker
        GlobalIndexSource.STOOQ -> stooqTicker
    }
}

object GlobalIndexRegistry {

    /** 코스피는 kotlin_krx 전용 — 시세 소스 매핑 대상에서 제외(리포지토리가 별도 처리) */
    const val KOSPI_NAME = "코스피"

    /** 도표48 순서와 동일한 19개 해외지수(코스피 제외) 매핑표 */
    val OVERSEAS_MARKETS: List<GlobalIndexSpec> = listOf(
        GlobalIndexSpec("닛케이", yahooTicker = "^N225", stooqTicker = "^nkx"),
        GlobalIndexSpec("대만", yahooTicker = "^TWII"),
        GlobalIndexSpec("다우", yahooTicker = "^DJI", stooqTicker = "^dji"),
        GlobalIndexSpec("CAC40", yahooTicker = "^FCHI"),
        GlobalIndexSpec("호주", yahooTicker = "^AXJO"),
        GlobalIndexSpec("유로", yahooTicker = "^STOXX50E"),
        GlobalIndexSpec("FTSE", yahooTicker = "^FTSE"),
        GlobalIndexSpec("태국", yahooTicker = "^SET.BK"),
        GlobalIndexSpec("베트남"), // VN지수 Yahoo 미제공
        GlobalIndexSpec("상하이", yahooTicker = "000001.SS"),
        GlobalIndexSpec("S&P", yahooTicker = "^GSPC", stooqTicker = "^spx"),
        GlobalIndexSpec("DAX", yahooTicker = "^GDAXI", stooqTicker = "^dax"),
        GlobalIndexSpec("나스닥", yahooTicker = "^IXIC", stooqTicker = "^ndq"),
        GlobalIndexSpec("인도", yahooTicker = "^NSEI"), // Nifty 50 (Sensex는 ^BSESN)
        GlobalIndexSpec("멕시코", yahooTicker = "^MXX"),
        GlobalIndexSpec("브라질", yahooTicker = "^BVSP"),
        GlobalIndexSpec("인니", yahooTicker = "^JKSE"),
        GlobalIndexSpec("항생", yahooTicker = "^HSI", stooqTicker = "^hsi"),
        GlobalIndexSpec("RTS") // 2022 제재 이후 Yahoo 종가 데이터 중단
    )

    /** 자동 수집 가능(코스피 제외) 지수만 */
    val AUTO_COVERED: List<GlobalIndexSpec> = OVERSEAS_MARKETS.filter { it.autoCovered }

    /** 수동 입력 필요 지수명 목록(코스피 제외) */
    val MANUAL_REQUIRED_NAMES: List<String> = OVERSEAS_MARKETS.filterNot { it.autoCovered }.map { it.name }
}
