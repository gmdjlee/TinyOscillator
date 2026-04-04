package com.tinyoscillator.domain.model

import kotlinx.serialization.Serializable

// ─── 2.11 Sector Correlation Network ───

/**
 * 섹터 상관관계 네트워크 분석 결과 (11번째 통계 엔진)
 *
 * Ledoit-Wolf 축소 추정량으로 섹터 내 종목 간 상관관계를 산출하고,
 * 그래프 기반 이상치 탐지로 섹터 클러스터에서 이탈한 종목을 찾는다.
 * 상관 붕괴(이탈)는 추세 전환의 선행 신호로 활용.
 */
@Serializable
data class SectorCorrelationResult(
    /** 대상 종목이 이상치(아웃라이어)인지 여부 */
    val isOutlier: Boolean,
    /** 이웃 종목들과의 평균 상관계수 */
    val meanNeighborCorr: Double,
    /** 그래프 내 이웃(연결) 수 */
    val nNeighbors: Int,
    /** 신호 점수 (0.0~1.0): 1.0=이상치(잠재적 전환), 0.5=정상 */
    val signalScore: Double,
    /** 섹터명 */
    val sectorName: String,
    /** 분석에 사용된 섹터 내 종목 수 */
    val nPeers: Int,
    /** Ledoit-Wolf 축소 강도 (0~1) */
    val shrinkageIntensity: Double,
    /** 상관 행렬의 평균 |상관계수| (네트워크 밀도 지표) */
    val avgAbsCorr: Double,
    /** 대상 종목의 섹터 내 상관 순위 (1=가장 낮은 상관) */
    val corrRank: Int = 0,
    /** 데이터 부족 등 사유 */
    val unavailableReason: String? = null
)
