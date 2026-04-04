package com.tinyoscillator.data.engine.calibration

import com.tinyoscillator.domain.model.HmmResult
import com.tinyoscillator.domain.model.RawSignalScore
import com.tinyoscillator.domain.model.StatisticalResult

/**
 * StatisticalResult에서 알고리즘별 원시 확률 점수를 추출.
 *
 * 각 엔진의 출력을 0~1 범위의 단일 "상승 확률" 스칼라로 변환한다.
 * CorrelationEngine은 확률을 생성하지 않으므로 제외.
 */
object SignalScoreExtractor {

    fun extract(result: StatisticalResult): List<RawSignalScore> {
        val scores = mutableListOf<RawSignalScore>()

        result.bayesResult?.let {
            scores.add(RawSignalScore("NaiveBayes", it.upProbability))
        }

        result.logisticResult?.let {
            scores.add(RawSignalScore("Logistic", it.probability))
        }

        result.hmmResult?.let {
            scores.add(RawSignalScore("HMM", regimeToBullishScore(it)))
        }

        result.patternAnalysis?.let { pa ->
            if (pa.activePatterns.isNotEmpty()) {
                val avgWinRate = pa.activePatterns.map { it.winRate20d }.average()
                scores.add(RawSignalScore("PatternScan", avgWinRate))
            }
        }

        result.signalScoringResult?.let {
            scores.add(RawSignalScore("SignalScoring", it.totalScore / 100.0))
        }

        result.bayesianUpdateResult?.let {
            scores.add(RawSignalScore("BayesianUpdate", it.finalPosterior))
        }

        result.orderFlowResult?.let {
            scores.add(RawSignalScore("OrderFlow", it.buyerDominanceScore))
        }

        result.dartEventResult?.let {
            if (it.nEvents > 0) {
                scores.add(RawSignalScore("DartEvent", it.signalScore))
            }
        }

        result.korea5FactorResult?.let {
            if (it.unavailableReason == null) {
                scores.add(RawSignalScore("Korea5Factor", it.signalScore))
            }
        }

        result.sectorCorrelationResult?.let {
            if (it.unavailableReason == null) {
                scores.add(RawSignalScore("SectorCorrelation", it.signalScore))
            }
        }

        return scores
    }

    /**
     * HMM 레짐을 상승 확률로 변환.
     * - 저변동 상승(0): 0.75
     * - 저변동 횡보(1): 0.50
     * - 고변동 상승(2): 0.65
     * - 고변동 하락(3): 0.20
     */
    private fun regimeToBullishScore(hmm: HmmResult): Double {
        val regimeScores = doubleArrayOf(0.75, 0.50, 0.65, 0.20)
        return hmm.regimeProbabilities.indices.sumOf { i ->
            val prob = if (i < hmm.regimeProbabilities.size) hmm.regimeProbabilities[i] else 0.0
            val score = if (i < regimeScores.size) regimeScores[i] else 0.5
            prob * score
        }
    }
}
