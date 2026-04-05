package com.tinyoscillator.core.testing.fake

import com.tinyoscillator.domain.model.AlgoResult
import com.tinyoscillator.domain.model.AnalysisBridge

/**
 * 테스트용 AnalysisBridge Fake.
 *
 * stubbedResults를 교체하면 다양한 시나리오(강세/약세/충돌)를 재현할 수 있다.
 */
class FakeSignalBridge : AnalysisBridge {

    var stubbedResults: Map<String, AlgoResult> = mapOf(
        "naive_bayes" to AlgoResult(
            score = 0.72f, rationale = "최근 20일 양봉 비율 68% — 강세 우세"
        ),
        "logistic_regression" to AlgoResult(
            score = 0.65f, rationale = "EMA5 > EMA20 골든크로스 확인"
        ),
        "hmm" to AlgoResult(
            score = 0.80f, rationale = "Bull Low-Vol 국면 (신뢰도 82%)"
        ),
        "cond_frequency" to AlgoResult(
            score = 0.58f, rationale = "조건부 상승 빈도 58% — 중립"
        ),
        "weighted_signal" to AlgoResult(
            score = 0.70f, rationale = "OBV 상승 + 거래량 20일 평균 초과"
        ),
        "rolling_correlation" to AlgoResult(
            score = 0.62f, rationale = "KOSPI 상관 0.74 — 지수 연동 중"
        ),
        "bayesian_updating" to AlgoResult(
            score = 0.75f, rationale = "외국인 순매수 5일 연속 — 사전확률 상향"
        ),
    )

    override suspend fun computeSignals(ticker: String): Map<String, AlgoResult> =
        stubbedResults

    override suspend fun getEnsembleScore(ticker: String): Float =
        stubbedResults.values.map { it.score }.average().toFloat()
}
