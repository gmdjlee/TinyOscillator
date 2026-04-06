package com.tinyoscillator.domain.model

enum class PatternSentiment { BULLISH, BEARISH, NEUTRAL }

/**
 * 박병창 매매의 기술 기반 매매 신호 유형.
 *
 * - 매수 3원칙 + 매도 2원칙 + 50% 룰 2종 = 총 7개 신호
 * - marker: 차트 위 배지 텍스트
 * - colorArgb: 0xAARRGGBB. 매수=녹색 계열, 매도=적색 계열, 50%룰=파란/주황
 */
enum class PatternType(
    val labelKo: String,
    val sentiment: PatternSentiment,
    val marker: String,
    val colorArgb: Long,
) {
    // ── 매수 신호 ──
    /** 매수 제1원칙: 5일선 위 추세 추종 매수 */
    BUY_TREND(
        "매수1·추세추종", PatternSentiment.BULLISH,
        "B1", 0xFF2E7D32,
    ),
    /** 매수 제2원칙: 5~20일선 사이 눌림목 반등 매수 */
    BUY_PULLBACK(
        "매수2·눌림목", PatternSentiment.BULLISH,
        "B2", 0xFF558B2F,
    ),
    /** 매수 제3원칙: 20일선 아래 거래량 폭증 역발상 매수 (고위험) */
    BUY_REVERSAL(
        "매수3·역발상", PatternSentiment.BULLISH,
        "B3", 0xFFE65100,
    ),

    // ── 매도 신호 ──
    /** 매도 제1원칙: 5일선 위 상투 징후 수익 실현 */
    SELL_TOP(
        "매도1·수익실현", PatternSentiment.BEARISH,
        "S1", 0xFFC62828,
    ),
    /** 매도 제2원칙: 5~20일선 사이 반등 실패 손절 */
    SELL_BREAKDOWN(
        "매도2·손절", PatternSentiment.BEARISH,
        "S2", 0xFF8B2020,
    ),

    // ── 50% 룰 ──
    /** 황소 50%: 전일 양봉 중간값 위 유지 → 매수 지속 */
    BULL_FIFTY(
        "황소50%·지지", PatternSentiment.BULLISH,
        "50\u2191", 0xFF1B5E20,
    ),
    /** 곰 50%: 전일 음봉 중간값 아래 반등 실패 → 매도 */
    BEAR_FIFTY(
        "곰50%·이탈", PatternSentiment.BEARISH,
        "50\u2193", 0xFF0D47A1,
    ),
}

data class PatternResult(
    val index: Int,
    val type: PatternType,
    val strength: Float,
)
