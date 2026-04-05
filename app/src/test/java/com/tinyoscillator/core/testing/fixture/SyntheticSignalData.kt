package com.tinyoscillator.core.testing.fixture

import com.tinyoscillator.domain.model.AlgoResult
import com.tinyoscillator.domain.model.SignalHistoryEntry

/**
 * 신호 투명성 테스트 전용 픽스처.
 *
 * 기존 SyntheticData(OHLCV/패턴)와 분리하여
 * 알고리즘 신호·앙상블·충돌 시나리오를 제공한다.
 */
object SyntheticSignalData {

    val ALGO_NAMES = listOf(
        "naive_bayes", "logistic_regression", "hmm",
        "cond_frequency", "weighted_signal", "rolling_correlation", "bayesian_updating"
    )

    /** 균일 분포 알고리즘 결과 (seed 기반 점수 생성) */
    fun algoResults(seed: Float = 0.7f): Map<String, AlgoResult> =
        ALGO_NAMES.mapIndexed { i, name ->
            val score = (seed + i * 0.03f).coerceIn(0f, 1f)
            name to AlgoResult(name, score, "테스트 근거 $i", 1f / ALGO_NAMES.size)
        }.toMap()

    /** 강세/약세 충돌 시나리오 (4:3 분할) */
    fun conflictingResults(): Map<String, AlgoResult> = mapOf(
        "naive_bayes" to AlgoResult(score = 0.82f, rationale = "강세"),
        "logistic_regression" to AlgoResult(score = 0.78f, rationale = "강세"),
        "hmm" to AlgoResult(score = 0.25f, rationale = "약세"),
        "cond_frequency" to AlgoResult(score = 0.22f, rationale = "약세"),
        "weighted_signal" to AlgoResult(score = 0.80f, rationale = "강세"),
        "rolling_correlation" to AlgoResult(score = 0.20f, rationale = "약세"),
        "bayesian_updating" to AlgoResult(score = 0.75f, rationale = "강세"),
    )

    /** 시그널 이력 생성 (기본 60일, 7개 알고리즘 순환) */
    fun signalHistory(
        ticker: String = "005930",
        count: Int = 60,
    ): List<SignalHistoryEntry> {
        val now = System.currentTimeMillis()
        return (0 until count).map { i ->
            SignalHistoryEntry(
                id = i.toLong(),
                ticker = ticker,
                date = now - i * 86_400_000L,
                algoName = ALGO_NAMES[i % ALGO_NAMES.size],
                signalScore = 0.5f + (i % 10) * 0.03f,
                outcomeT1 = if (i < count - 2) ((i % 3) - 1).toFloat() else null,
                outcomeT5 = if (i < count - 6) ((i % 2) * 0.02f) else null,
                outcomeT20 = null,
            )
        }
    }
}
