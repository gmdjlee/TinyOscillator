package com.tinyoscillator.domain.model

/**
 * 분석 엔진 브릿지 인터페이스
 *
 * 통계 엔진 → UI 간 신호 투명성 레이어.
 * 테스트에서는 FakeSignalBridge로 대체 가능.
 */
interface AnalysisBridge {
    /** 종목별 알고리즘 신호를 계산하여 반환 */
    suspend fun computeSignals(ticker: String): Map<String, AlgoResult>

    /** 앙상블 종합 점수 (0~1) */
    suspend fun getEnsembleScore(ticker: String): Float
}
