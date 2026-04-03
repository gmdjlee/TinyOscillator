package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable

// ─── BOK ECOS 매크로 지표 모델 ───

/**
 * ECOS 통계 지표 정의
 *
 * @param statCode 통계표코드
 * @param freq 주기 (M=월간, Q=분기)
 * @param itemCode 통계항목코드
 */
data class EcosIndicatorSpec(
    val statCode: String,
    val freq: String,
    val itemCode: String
)

/**
 * ECOS API에서 가져온 원시 데이터 포인트
 */
data class EcosDataPoint(
    val time: String,       // yyyyMM (월간)
    val value: Double
)

/**
 * 매크로 환경 분류
 *
 * 한국은행 기준금리, M2, 산업생산지수, 환율, CPI의
 * YoY 변화율을 기반으로 4가지 매크로 환경을 분류.
 */
enum class MacroEnvironment(val label: String, val description: String) {
    EASING("완화", "금리 인하 또는 유동성 확대 국면 — 위험자산 선호"),
    TIGHTENING("긴축", "금리 인상 또는 유동성 축소 국면 — 안전자산 선호"),
    NEUTRAL("중립", "특별한 방향성 없는 안정 국면"),
    STAGFLATION("스태그플레이션", "경기 침체 + 물가 상승 — 방어적 포지션 필요");

    companion object {
        fun fromString(s: String): MacroEnvironment =
            entries.find { it.name == s } ?: NEUTRAL
    }
}

/**
 * 매크로 신호 결과
 *
 * 5개 BOK ECOS 지표의 YoY 변화율 + 매크로 환경 분류
 */
@Serializable
data class MacroSignalResult(
    /** 기준금리 YoY 변화 (pp) */
    val baseRateYoy: Double,
    /** M2 통화량 YoY 변화율 (%) */
    val m2Yoy: Double,
    /** 산업생산지수 YoY 변화율 (%) */
    val iipYoy: Double,
    /** USD/KRW 환율 YoY 변화율 (%) */
    val usdKrwYoy: Double,
    /** 소비자물가지수 YoY 변화율 (%) */
    val cpiYoy: Double,
    /** 매크로 환경 분류 */
    val macroEnv: String,
    /** 기준 연월 (yyyyMM) */
    val referenceMonth: String = "",
    /** 데이터 수집 시각 (epoch ms) */
    val fetchedAt: Long = System.currentTimeMillis(),
    /** 사용 불가 사유 (null = 정상) */
    val unavailableReason: String? = null
)
