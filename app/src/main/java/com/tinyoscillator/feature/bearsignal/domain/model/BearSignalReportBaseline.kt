package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 리포트 기준값 스냅샷 — 신영증권 「주도주의 물리학」(2026.6.30), TASK.md 부록 C.
 *
 * 골든 케이스: 아래 스칼라 + 도표48 마켓 데이터(MARKETS 20지수) → s1=1, s2=1, s3=1, gate=1, amp=1.30 → AMBER.
 * 값 변경은 리포트 근거 + 골든 테스트 갱신 동반 시에만 허용 (§8).
 */
object BearSignalReportBaseline {

    const val REPORT_DATE = "2026-06-30"

    /** §4.6 스냅샷 계약 `config_basis` 필드값 — [com.tinyoscillator.feature.bearsignal.domain.usecase.BuildBearSnapshotUseCase] 기본값. */
    const val CONFIG_BASIS = "신영 2026.6.30"

    // ── 기준값 스칼라 (부록 C) ──────────────────────────────
    /** 기간 인덱스: 0=12M, 1=6M, 2=3M, 3=1M */
    const val PERIOD_IDX = 3
    const val UP = 14
    const val DOWN = 12
    const val DEEPENING = true
    const val LOSS = 45.0
    const val ETF = "up"
    const val BIG = "pending"
    const val RATE = 3.75
    const val DIR = "hike"
    const val CREDIT = 38.0
    const val MARGIN = false
    const val SEMI = 23.1
    const val KOSPI2 = 56.0
    const val BUFFER = true

    // ── 도표48 국가별 수익률 시드 (20지수 × [-12M,-6M,-3M,-1M]) ──────────────────────────
    // 프로토타입 bear_signal_dashboard.jsx의 `MARKETS` 상수를 순서·값 그대로 이관 (부록 C SSOT).
    val KOSPI = MarketReturns("코스피", listOf(173.1, 103.7, 54.0, 4.5), lead = true)
    val NIKKEI = MarketReturns("닛케이", listOf(75.2, 36.7, 29.4, 6.7))
    val TAIWAN = MarketReturns("대만", listOf(98.2, 56.1, 33.7, 2.4))
    val DOW = MarketReturns("다우", listOf(19.6, 6.5, 12.9, 2.8))
    val CAC40 = MarketReturns("CAC40", listOf(11.0, 3.5, 7.9, 2.6))
    val AUSTRALIA = MarketReturns("호주", listOf(2.5, 0.0, 2.8, 1.2))
    val EURO = MarketReturns("유로", listOf(18.6, 8.2, 11.8, 2.6))
    val FTSE = MarketReturns("FTSE", listOf(20.3, 6.5, 5.4, 0.2))
    val THAILAND = MarketReturns("태국", listOf(39.4, 22.5, 6.9, -0.7))
    val VIETNAM = MarketReturns("베트남", listOf(37.1, 8.2, 13.8, -0.7))
    val SHANGHAI = MarketReturns("상하이", listOf(16.8, 1.6, 3.6, -2.8))
    val SNP500 = MarketReturns("S&P", listOf(19.8, 6.1, 13.5, -2.2))
    val DAX = MarketReturns("DAX", listOf(4.3, 1.4, 9.1, -2.0))
    val NASDAQ = MarketReturns("나스닥", listOf(25.4, 7.2, 18.2, -5.1))
    val INDIA = MarketReturns("인도", listOf(-5.8, -7.6, 3.2, 0.6))
    val MEXICO = MarketReturns("멕시코", listOf(17.0, 2.4, 0.2, -2.8))
    val BRAZIL = MarketReturns("브라질", listOf(26.4, 8.0, -5.2, -1.9))
    val INDONESIA = MarketReturns("인니", listOf(-14.5, -30.9, -17.7, -3.8))
    val HANG_SENG = MarketReturns("항생", listOf(-6.8, -12.2, -8.8, -11.4))
    val RTS = MarketReturns("RTS", listOf(-17.1, -16.4, -13.7, -17.6))

    /** 도표48 전체 20지수 시드 (프로토타입 jsx `MARKETS` 배열과 순서 동일) */
    val MARKETS: List<MarketReturns> = listOf(
        KOSPI, NIKKEI, TAIWAN, DOW, CAC40, AUSTRALIA, EURO, FTSE, THAILAND, VIETNAM,
        SHANGHAI, SNP500, DAX, NASDAQ, INDIA, MEXICO, BRAZIL, INDONESIA, HANG_SENG, RTS
    )

    /** 기준값 스칼라 + 주어진 마켓 데이터로 스코어링 입력 구성 (기본값 = 도표48 전체 시드) */
    fun toInputs(markets: List<MarketReturns> = MARKETS): BearSignalInputs = BearSignalInputs(
        markets = markets,
        periodIdx = PERIOD_IDX,
        up = UP,
        down = DOWN,
        deepening = DEEPENING,
        loss = LOSS,
        etf = ETF,
        big = BIG,
        rate = RATE,
        dir = DIR,
        credit = CREDIT,
        margin = MARGIN,
        semi = SEMI,
        kospi2 = KOSPI2,
        buffer = BUFFER
    )
}
