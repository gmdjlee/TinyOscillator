package com.tinyoscillator.data.engine

import com.tinyoscillator.data.engine.calibration.SignalScoreExtractor
import com.tinyoscillator.data.engine.regime.RegimeWeightTable
import com.tinyoscillator.domain.model.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * StatisticalResult에서 알고리즘별 AlgoResult(점수 + 한국어 근거)를 생성.
 *
 * 근거 문자열 규칙:
 * - 최대 50자 이내 한국어
 * - 항상 숫자 포함 (정량화)
 * - 방향 판단 포함 ("강세"/"약세"/"중립")
 */
object RationaleBuilder {

    /**
     * StatisticalResult → 알고리즘별 AlgoResult 맵
     *
     * @param result 분석 결과
     * @param weights 레짐 가중치 (null이면 균등 가중치)
     */
    fun build(
        result: StatisticalResult,
        weights: Map<String, Double> = RegimeWeightTable.equalWeights()
    ): Map<String, AlgoResult> {
        val rawScores = SignalScoreExtractor.extract(result)
        val scoreMap = rawScores.associate { it.algoName to it.rawScore }
        val out = mutableMapOf<String, AlgoResult>()

        result.bayesResult?.let { bayes ->
            val score = scoreMap["NaiveBayes"] ?: bayes.upProbability
            out["NaiveBayes"] = AlgoResult(
                algoName = "NaiveBayes",
                score = score.toFloat(),
                rationale = buildBayesRationale(bayes),
                weight = weights["NaiveBayes"]?.toFloat() ?: 0f
            )
        }

        result.logisticResult?.let { lr ->
            val score = scoreMap["Logistic"] ?: lr.probability
            out["Logistic"] = AlgoResult(
                algoName = "Logistic",
                score = score.toFloat(),
                rationale = buildLogisticRationale(lr),
                weight = weights["Logistic"]?.toFloat() ?: 0f
            )
        }

        result.hmmResult?.let { hmm ->
            val score = scoreMap["HMM"] ?: 0.5
            out["HMM"] = AlgoResult(
                algoName = "HMM",
                score = score.toFloat(),
                rationale = buildHmmRationale(hmm),
                weight = weights["HMM"]?.toFloat() ?: 0f
            )
        }

        result.patternAnalysis?.let { pa ->
            val score = scoreMap["PatternScan"]
            if (score != null) {
                out["PatternScan"] = AlgoResult(
                    algoName = "PatternScan",
                    score = score.toFloat(),
                    rationale = buildPatternRationale(pa),
                    weight = weights["PatternScan"]?.toFloat() ?: 0f
                )
            }
        }

        result.signalScoringResult?.let { ss ->
            val score = scoreMap["SignalScoring"] ?: (ss.totalScore / 100.0)
            out["SignalScoring"] = AlgoResult(
                algoName = "SignalScoring",
                score = score.toFloat(),
                rationale = buildSignalRationale(ss),
                weight = weights["SignalScoring"]?.toFloat() ?: 0f
            )
        }

        result.bayesianUpdateResult?.let { bu ->
            val score = scoreMap["BayesianUpdate"] ?: bu.finalPosterior
            out["BayesianUpdate"] = AlgoResult(
                algoName = "BayesianUpdate",
                score = score.toFloat(),
                rationale = buildBayesianRationale(bu),
                weight = weights["BayesianUpdate"]?.toFloat() ?: 0f
            )
        }

        result.orderFlowResult?.let { of ->
            val score = scoreMap["OrderFlow"] ?: of.buyerDominanceScore
            out["OrderFlow"] = AlgoResult(
                algoName = "OrderFlow",
                score = score.toFloat(),
                rationale = buildOrderFlowRationale(of),
                weight = weights["OrderFlow"]?.toFloat() ?: 0f
            )
        }

        result.dartEventResult?.let { de ->
            val score = scoreMap["DartEvent"]
            if (score != null) {
                out["DartEvent"] = AlgoResult(
                    algoName = "DartEvent",
                    score = score.toFloat(),
                    rationale = buildDartEventRationale(de),
                    weight = weights["DartEvent"]?.toFloat() ?: 0f
                )
            }
        }

        result.korea5FactorResult?.let { k5 ->
            val score = scoreMap["Korea5Factor"]
            if (score != null) {
                out["Korea5Factor"] = AlgoResult(
                    algoName = "Korea5Factor",
                    score = score.toFloat(),
                    rationale = buildKorea5FactorRationale(k5),
                    weight = weights["Korea5Factor"]?.toFloat() ?: 0f
                )
            }
        }

        result.sectorCorrelationResult?.let { sc ->
            val score = scoreMap["SectorCorrelation"]
            if (score != null) {
                out["SectorCorrelation"] = AlgoResult(
                    algoName = "SectorCorrelation",
                    score = score.toFloat(),
                    rationale = buildSectorCorrRationale(sc),
                    weight = weights["SectorCorrelation"]?.toFloat() ?: 0f
                )
            }
        }

        return out
    }

    // ─── 개별 알고리즘 근거 생성 ───

    private fun buildBayesRationale(bayes: BayesResult): String {
        val upPct = (bayes.upProbability * 100).roundToInt()
        val direction = direction(bayes.upProbability)
        val topFeature = bayes.dominantFeatures.firstOrNull()?.featureName ?: ""
        return if (topFeature.isNotEmpty()) {
            "상승${upPct}% $topFeature 기여 — $direction"
        } else {
            "상승${upPct}% 샘플${bayes.sampleCount}건 — $direction"
        }.take(50)
    }

    private fun buildLogisticRationale(lr: LogisticResult): String {
        val score = lr.score0to100
        val direction = direction(lr.probability)
        val topFeature = lr.featureValues.maxByOrNull { abs(it.value) }
        return if (topFeature != null) {
            "점수${score}/100 ${topFeature.key} ${fmtVal(topFeature.value)} — $direction"
        } else {
            "점수${score}/100 확률${(lr.probability * 100).roundToInt()}% — $direction"
        }.take(50)
    }

    private fun buildHmmRationale(hmm: HmmResult): String {
        val conf = (hmm.regimeProbabilities.getOrNull(hmm.currentRegime)?.times(100))?.roundToInt() ?: 0
        val regimeName = when (hmm.currentRegime) {
            0 -> "저변동상승"
            1 -> "저변동횡보"
            2 -> "고변동상승"
            3 -> "고변동하락"
            else -> "미확인"
        }
        val direction = when (hmm.currentRegime) {
            0, 2 -> "강세"
            3 -> "약세"
            else -> "중립"
        }
        return "${regimeName} 신뢰${conf}% — $direction".take(50)
    }

    private fun buildPatternRationale(pa: PatternAnalysis): String {
        val active = pa.activePatterns.size
        if (active == 0) return "활성 패턴 없음 — 중립"
        val best = pa.activePatterns.maxByOrNull { it.winRate20d }!!
        val winPct = (best.winRate20d * 100).roundToInt()
        val direction = direction(best.winRate20d)
        return "${active}개 활성 최고승률${winPct}% — $direction".take(50)
    }

    private fun buildSignalRationale(ss: SignalScoringResult): String {
        val score = ss.totalScore
        val dir = when (ss.dominantDirection) {
            "BULLISH" -> "강세"
            "BEARISH" -> "약세"
            else -> "중립"
        }
        val topContrib = ss.contributions.maxByOrNull { it.contributionPercent }
        return if (topContrib != null) {
            "점수${score}/100 ${topContrib.name} ${topContrib.contributionPercent.roundToInt()}% — $dir"
        } else {
            "점수${score}/100 — $dir"
        }.take(50)
    }

    private fun buildBayesianRationale(bu: BayesianUpdateResult): String {
        val prior = (bu.priorProbability * 100).roundToInt()
        val post = (bu.finalPosterior * 100).roundToInt()
        val delta = post - prior
        val arrow = if (delta > 0) "↑" else if (delta < 0) "↓" else "→"
        val direction = direction(bu.finalPosterior)
        return "사전${prior}%→사후${post}% ${arrow}${abs(delta)}p — $direction".take(50)
    }

    private fun buildOrderFlowRationale(of: OrderFlowResult): String {
        val scorePct = (of.buyerDominanceScore * 100).roundToInt()
        val dir = when (of.flowDirection) {
            "BUY" -> "강세"
            "SELL" -> "약세"
            else -> "중립"
        }
        val strength = when (of.flowStrength) {
            "STRONG" -> "강"
            "MODERATE" -> "보통"
            else -> "약"
        }
        return "매수우위${scorePct}% 강도${strength} — $dir".take(50)
    }

    private fun buildDartEventRationale(de: DartEventResult): String {
        if (de.nEvents == 0) return "최근 공시 없음 — 중립"
        val scorePct = (de.signalScore * 100).roundToInt()
        val carPct = String.format("%+.1f", de.latestCar * 100)
        val direction = direction(de.signalScore)
        return "${de.nEvents}건 CAR${carPct}% 신호${scorePct}% — $direction".take(50)
    }

    private fun buildKorea5FactorRationale(k5: Korea5FactorResult): String {
        if (k5.unavailableReason != null) return "데이터 부족 — 중립"
        val scorePct = (k5.signalScore * 100).roundToInt()
        val alphaZ = String.format("%+.1f", k5.alphaZscore)
        val direction = direction(k5.signalScore)
        return "알파z=${alphaZ} 신호${scorePct}% — $direction".take(50)
    }

    private fun buildSectorCorrRationale(sc: SectorCorrelationResult): String {
        if (sc.unavailableReason != null) return "데이터 부족 — 중립"
        val scorePct = (sc.signalScore * 100).roundToInt()
        val status = if (sc.isOutlier) "이상치" else "정상"
        val direction = direction(sc.signalScore)
        return "${sc.sectorName} ${status} 신호${scorePct}% — $direction".take(50)
    }

    // ─── 공통 유틸 ───

    private fun direction(score: Double): String = when {
        score >= 0.65 -> "강세"
        score <= 0.35 -> "약세"
        else -> "중립"
    }

    private fun fmtVal(v: Double): String = when {
        abs(v) >= 100 -> String.format("%.0f", v)
        abs(v) >= 1 -> String.format("%.1f", v)
        else -> String.format("%.3f", v)
    }
}
