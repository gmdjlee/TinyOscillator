package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable

// ─── 2.10 Korea 5-Factor Model ───

/**
 * 한국형 5팩터 모델 분석 결과 (10번째 통계 엔진)
 *
 * Fama-French 5팩터를 한국 시장에 적용:
 * - MKT: 시장 초과수익률 (KOSPI - 기준금리)
 * - SMB: 소형주 프리미엄 (시가총액 기반)
 * - HML: 가치주 프리미엄 (PBR 역수 기반)
 * - RMW: 수익성 프리미엄 (ROE 기반)
 * - CMA: 투자보수성 프리미엄 (자산성장률 기반)
 */
@Serializable
data class Korea5FactorResult(
    /** 최근 rolling alpha (팩터 조정 초과 수익률) */
    val alphaRaw: Double,
    /** alpha z-score (최근 12개 관측치 대비 표준화) */
    val alphaZscore: Double,
    /** 시그모이드 변환 신호 점수 (0.0~1.0, 0.5=중립) */
    val signalScore: Double,
    /** OLS 회귀에 사용된 관측치 수 */
    val nObs: Int,
    /** 마지막 분석 기준일 (yyyyMM) */
    val lastDate: String,
    /** 5팩터 베타 계수 */
    val betas: FactorBetas,
    /** R² (결정계수) */
    val rSquared: Double = 0.0,
    /** 롤링 윈도우 크기 (개월) */
    val windowMonths: Int = 36,
    /** 데이터 부족 등 사유 */
    val unavailableReason: String? = null
)

/**
 * Fama-French 5팩터 베타 계수
 */
@Serializable
data class FactorBetas(
    val mkt: Double = 0.0,
    val smb: Double = 0.0,
    val hml: Double = 0.0,
    val rmw: Double = 0.0,
    val cma: Double = 0.0
) {
    fun toMap(): Map<String, Double> = mapOf(
        "MKT" to mkt, "SMB" to smb, "HML" to hml,
        "RMW" to rmw, "CMA" to cma
    )
}

/**
 * 월별 팩터 수익률 데이터 (캐시용)
 */
@Serializable
data class MonthlyFactorRow(
    val yearMonth: String,   // yyyyMM
    val mktExcess: Double,   // 시장 초과수익률
    val smb: Double,         // 소형주 프리미엄
    val hml: Double,         // 가치주 프리미엄
    val rmw: Double,         // 수익성 프리미엄
    val cma: Double          // 투자보수성 프리미엄
)

/**
 * 팩터 데이터 캐시 (FeatureStore에 저장)
 */
@Serializable
data class FactorDataCache(
    val rows: List<MonthlyFactorRow>,
    val computedAt: Long = System.currentTimeMillis()
)
