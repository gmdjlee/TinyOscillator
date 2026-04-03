package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable

/**
 * Kelly Criterion + CVaR 기반 포지션 사이징 추천 결과
 *
 * 분석 참고용 — 투자 조언이 아님.
 */

/** 포지션 크기 제한 사유 */
@Serializable
enum class SizeReasonCode(val label: String) {
    KELLY_BOUND("켈리 제한"),
    CVAR_BOUND("CVaR 제한"),
    MAX_POSITION("최대 비중 제한"),
    NO_EDGE("신호 우위 없음");

    companion object {
        fun toKorean(code: SizeReasonCode): String = code.label
    }
}

/** 포지션 사이징 추천 결과 */
@Serializable
data class PositionRecommendation(
    /** 종목코드 */
    val ticker: String,
    /** 추천 포지션 비중 (0.0~1.0, 0.0 = 투자하지 않음) */
    val recommendedPct: Double,
    /** 원시 Kelly 비율 (f*) */
    val kellyRaw: Double,
    /** Fractional Kelly (f* × fraction) */
    val kellyFractional: Double,
    /** 변동성 조정 후 Kelly 비중 */
    val volAdjustedSize: Double,
    /** 1일 CVaR (95% 신뢰수준, 음수 = 손실) */
    val cvar1d: Double,
    /** CVaR 기반 포지션 한도 (0.0~1.0) */
    val cvarLimit: Double,
    /** 신호 우위 (signal_prob - 0.5, 양수 = 우위 있음) */
    val signalEdge: Double,
    /** 20일 실현 변동성 (연율화) */
    val realizedVol: Double,
    /** Win/Loss Ratio */
    val winLossRatio: Double,
    /** 크기 제한 사유 */
    val sizeReasonCode: SizeReasonCode,
    /** 사용 불가 사유 (데이터 부족 등, null이면 정상) */
    val unavailableReason: String? = null
)

/** Kelly 사이징 중간 결과 */
@Serializable
data class KellyResult(
    val rawKelly: Double,
    val fracKelly: Double,
    val volAdjSize: Double,
    val signalEdge: Double,
    val recommendedPct: Double
)
