package com.tinyoscillator.domain.model

/**
 * 신호 투명성(Signal Transparency) 관련 도메인 모델
 *
 * AlgoResult: 개별 알고리즘의 캘리브레이션 된 확률 + 근거 문자열
 * SignalHistoryEntry: 시그널 이력 (테스트/UI용 도메인 모델, Room Entity와 분리)
 */

/** 알고리즘별 캘리브레이션 된 확률 + 근거 */
data class AlgoResult(
    val algoName: String = "",
    /** 캘리브레이션 된 확률 [0, 1] */
    val score: Float,
    /** 한국어 근거 문자열 */
    val rationale: String = "",
    /** 앙상블 가중치 (0~1, 합=1) */
    val weight: Float = 0f,
)

/** 시그널 이력 엔트리 (도메인 모델) */
data class SignalHistoryEntry(
    val id: Long = 0,
    val ticker: String,
    val date: Long,
    val algoName: String,
    val signalScore: Float,
    /** T+1 수익률 (아직 미확정이면 null) */
    val outcomeT1: Float? = null,
    /** T+5 수익률 */
    val outcomeT5: Float? = null,
    /** T+20 수익률 */
    val outcomeT20: Float? = null,
)

/** 알고리즘별 적중률 집계 행 (Room @Query 결과 매핑) */
data class AlgoAccuracyRow(
    val algoName: String,
    val total: Int,
    val hits: Int,
) {
    val accuracy: Float get() = if (total == 0) 0f else hits.toFloat() / total
}
