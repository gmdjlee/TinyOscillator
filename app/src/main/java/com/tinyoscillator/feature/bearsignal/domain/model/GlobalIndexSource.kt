package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 해외지수·IPO ETF 일별 시세 소스 (TASK.md §4 "해외 19개 지수", "IPO ETF 방향").
 *
 * Stooq가 2026-07 안티봇(JS PoW) 차단으로 자동 수집이 전멸하면서(QA 결과), Yahoo Finance
 * chart API(무인증 JSON)를 기본 소스로 채택하고 Stooq는 백업 선택지로 유지한다. 사용자가
 * 설정에서 기본 소스를 고를 수 있고, 선택 소스 실패(차단·빈 응답) 시 리포지토리가 나머지
 * 소스로 자동 폴백한다.
 *
 * **검토 후 탈락한 후보**: FRED(S&P/다우/나스닥만 커버 — DAX·항생·IPO ETF 불가),
 * Alpha Vantage(무료 티어 지수 미지원), Twelve Data(지수는 유료 플랜), FMP(무료 티어 제한),
 * investing.com 등 스크래핑(ToS·구조 변경 취약). 두 소스 모두 인증키 불필요.
 */
enum class GlobalIndexSource(val displayName: String) {
    /** Yahoo Finance chart API — `query1.finance.yahoo.com/v8/finance/chart/{symbol}` (기본) */
    YAHOO("Yahoo Finance"),

    /** Stooq 무료 CSV — `stooq.com/q/d/l/?s={ticker}&i=d` (백업, 2026-07 현재 봇 차단 관측) */
    STOOQ("Stooq");

    companion object {
        val DEFAULT = YAHOO

        /** 저장된 이름 → enum 복원 (미지·null이면 [DEFAULT]) */
        fun fromName(name: String?): GlobalIndexSource = entries.find { it.name == name } ?: DEFAULT
    }
}
