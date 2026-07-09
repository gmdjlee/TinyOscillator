package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 도표48 20개 지수 ↔ 자동 수집 소스 매핑표 (TASK.md §4 "해외 19개 지수", §8 리스크).
 *
 * 코스피는 `kotlin_krx get_index_ohlcv("1001")`로 별도 수집(§1.1 [A], 기존 [BearSignalRepositoryImpl]
 * 재사용). 나머지 19개 해외지수는 무료 CSV(Stooq, `https://stooq.com/q/d/l/?s={ticker}&i=d`)로
 * 커버 가능한 지수만 [GlobalIndexSpec.stooqTicker]를 채운다.
 *
 * **커버리지 결정 기준**: Stooq 지수 티커는 검증 가능한 공개 문서·공지 기준으로 신뢰도가 높은
 * 6개 주요 지수(다우·S&P·나스닥·DAX·닛케이·항생)만 [MarketCoverage.AUTO]로 채택했다. 나머지
 * 13개(대만·CAC40·호주·유로·FTSE·태국·베트남·상하이·인도·멕시코·브라질·인니·RTS)는 티커 표기
 * 불확실성(거래소별 접미사·심볼 변경) 및 RTS의 접근성 이슈로 [MarketCoverage.MANUAL_REQUIRED]로
 * 유지한다 — TASK.md §1.1 각주1이 명시적으로 허용하는 폴백이다. v2에서 실기 검증 후 확장한다.
 *
 * [BearSignalReportBaseline.MARKETS]와 이름·순서가 동일하다(20개 지수 SSOT는 그대로 유지, 매핑만 추가).
 */
data class GlobalIndexSpec(
    val name: String,
    val lead: Boolean = false,
    /** Stooq CSV 심볼 (예: `^dji`). null이면 자동 수집 불가 → [MarketCoverage.MANUAL_REQUIRED] */
    val stooqTicker: String?
)

object GlobalIndexRegistry {

    /** 코스피는 kotlin_krx 전용 — Stooq 매핑 대상에서 제외(리포지토리가 별도 처리) */
    const val KOSPI_NAME = "코스피"

    /** 도표48 순서와 동일한 19개 해외지수(코스피 제외) 매핑표 */
    val OVERSEAS_MARKETS: List<GlobalIndexSpec> = listOf(
        GlobalIndexSpec("닛케이", stooqTicker = "^nkx"),
        GlobalIndexSpec("대만", stooqTicker = null),
        GlobalIndexSpec("다우", stooqTicker = "^dji"),
        GlobalIndexSpec("CAC40", stooqTicker = null),
        GlobalIndexSpec("호주", stooqTicker = null),
        GlobalIndexSpec("유로", stooqTicker = null),
        GlobalIndexSpec("FTSE", stooqTicker = null),
        GlobalIndexSpec("태국", stooqTicker = null),
        GlobalIndexSpec("베트남", stooqTicker = null),
        GlobalIndexSpec("상하이", stooqTicker = null),
        GlobalIndexSpec("S&P", stooqTicker = "^spx"),
        GlobalIndexSpec("DAX", stooqTicker = "^dax"),
        GlobalIndexSpec("나스닥", stooqTicker = "^ndq"),
        GlobalIndexSpec("인도", stooqTicker = null),
        GlobalIndexSpec("멕시코", stooqTicker = null),
        GlobalIndexSpec("브라질", stooqTicker = null),
        GlobalIndexSpec("인니", stooqTicker = null),
        GlobalIndexSpec("항생", stooqTicker = "^hsi"),
        GlobalIndexSpec("RTS", stooqTicker = null)
    )

    /** 자동 수집 가능(코스피 제외) 지수만 */
    val AUTO_COVERED: List<GlobalIndexSpec> = OVERSEAS_MARKETS.filter { it.stooqTicker != null }

    /** 수동 입력 필요 지수명 목록(코스피 제외) */
    val MANUAL_REQUIRED_NAMES: List<String> = OVERSEAS_MARKETS.filter { it.stooqTicker == null }.map { it.name }
}
