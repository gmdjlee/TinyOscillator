package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.BayesResult
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.FeatureContribution
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 나이브 베이즈 분류기 — P(상승|지표조합) 산출
 *
 * 각 지표를 이산 상태로 변환 후, 과거 데이터에서 조건부 확률을 카운팅.
 * Laplace smoothing(alpha=1) 적용으로 zero probability 방지.
 * 3-class 분류: UP(20일후 >+2%), DOWN(20일후 <-2%), SIDEWAYS(나머지).
 */
@Singleton
class NaiveBayesEngine @Inject constructor() {

    companion object {
        private const val LOOK_AHEAD_DAYS = 20
        private const val UP_THRESHOLD = 0.02
        private const val DOWN_THRESHOLD = -0.02
        private const val LAPLACE_ALPHA = 1.0

        // 거래량 판단용 평균 윈도우
        private const val VOLUME_AVG_WINDOW = 20
    }

    // 3-class 라벨
    enum class Label { UP, DOWN, SIDEWAYS }

    // 지표별 이산 상태
    enum class MacdState { POSITIVE, NEGATIVE, NEAR_ZERO }
    enum class OscillatorState { BUY, SELL, NEUTRAL }
    enum class EmaState { GOLDEN, DEAD, CONVERGING }
    enum class DemarkState { SETUP_HIGH, SETUP_LOW, NONE }
    enum class VolumeState { SURGE, NORMAL, LOW }
    enum class PbrState { UNDERVALUED, FAIR, OVERVALUED }

    /**
     * 나이브 베이즈 분류 실행
     *
     * @param prices 일별 거래 데이터 (날짜 오름차순)
     * @param oscillators 오실레이터 데이터 (날짜 오름차순)
     * @param demarkRows DeMark TD 데이터 (날짜 오름차순)
     * @param fundamentals 펀더멘털 데이터 (날짜 오름차순, nullable)
     */
    suspend fun analyze(
        prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>,
        demarkRows: List<DemarkTDRow>,
        fundamentals: List<FundamentalSnapshot>?
    ): BayesResult {
        require(prices.size >= LOOK_AHEAD_DAYS + 5) {
            "최소 ${LOOK_AHEAD_DAYS + 5}일의 가격 데이터가 필요합니다 (현재: ${prices.size})"
        }

        // 날짜 기반 인덱스 맵
        val priceByDate = prices.associateBy { it.date }
        val oscByDate = oscillators.associateBy { it.date }
        val demarkByDate = demarkRows.associateBy { it.date }
        val fundByDate = fundamentals?.associateBy { it.date } ?: emptyMap()

        // 거래량(시가총액 변화율) 평균 계산용
        val volumeChanges = calcVolumeChanges(prices)

        // 학습 데이터 생성: 각 시점의 지표 상태 + 라벨
        val trainingData = mutableListOf<Pair<Map<String, String>, Label>>()

        for (i in 0 until prices.size - LOOK_AHEAD_DAYS) {
            val date = prices[i].date
            val futurePrice = prices[i + LOOK_AHEAD_DAYS].closePrice
            val currentPrice = prices[i].closePrice
            if (currentPrice == 0) continue

            val returnRate = (futurePrice - currentPrice).toDouble() / currentPrice
            val label = classifyReturn(returnRate)

            val features = extractFeatures(
                date, oscByDate, demarkByDate, fundByDate, volumeChanges
            ) ?: continue

            trainingData.add(features to label)
        }

        if (trainingData.isEmpty()) {
            return BayesResult(
                upProbability = 1.0 / 3,
                downProbability = 1.0 / 3,
                sidewaysProbability = 1.0 / 3,
                dominantFeatures = emptyList(),
                sampleCount = 0
            )
        }

        // 클래스별 빈도 카운팅
        val classCounts = mutableMapOf(
            Label.UP to 0, Label.DOWN to 0, Label.SIDEWAYS to 0
        )
        trainingData.forEach { (_, label) -> classCounts[label] = classCounts[label]!! + 1 }
        val totalSamples = trainingData.size
        val numClasses = 3

        // 각 피처-값-클래스별 빈도 카운팅
        val featureValueClassCounts = mutableMapOf<String, MutableMap<String, MutableMap<Label, Int>>>()
        val featureValues = mutableMapOf<String, MutableSet<String>>()

        for ((features, label) in trainingData) {
            for ((featureName, featureValue) in features) {
                featureValues.getOrPut(featureName) { mutableSetOf() }.add(featureValue)
                featureValueClassCounts
                    .getOrPut(featureName) { mutableMapOf() }
                    .getOrPut(featureValue) { mutableMapOf() }
                    .merge(label, 1) { a, b -> a + b }
            }
        }

        // 현재 시점의 지표 상태로 예측
        val currentDate = prices.last().date
        val currentFeatures = extractFeatures(
            currentDate, oscByDate, demarkByDate, fundByDate, volumeChanges
        ) ?: return BayesResult(1.0 / 3, 1.0 / 3, 1.0 / 3, emptyList(), totalSamples)

        // 각 클래스별 로그 사후 확률 계산
        val logPosteriors = mutableMapOf<Label, Double>()
        val featureContributions = mutableMapOf<String, Double>()

        for (label in Label.entries) {
            var logProb = Math.log(
                (classCounts[label]!! + LAPLACE_ALPHA) /
                        (totalSamples + numClasses * LAPLACE_ALPHA)
            )

            for ((featureName, featureValue) in currentFeatures) {
                val numPossibleValues = featureValues[featureName]?.size ?: 1
                val count = featureValueClassCounts[featureName]
                    ?.get(featureValue)?.get(label) ?: 0
                val classCount = classCounts[label]!!

                val likelihood = (count + LAPLACE_ALPHA) /
                        (classCount + numPossibleValues * LAPLACE_ALPHA)

                logProb += Math.log(likelihood)

                // UP 클래스의 likelihood ratio 계산 (feature contribution)
                if (label == Label.UP) {
                    val totalFeatureCount = trainingData.count { (f, _) -> f[featureName] == featureValue }
                    val marginalProb = if (totalFeatureCount > 0)
                        totalFeatureCount.toDouble() / totalSamples else 1.0 / numPossibleValues
                    featureContributions[featureName] = likelihood / marginalProb
                }
            }

            logPosteriors[label] = logProb
        }

        // 로그 확률 → 확률 변환 (오버플로우 방지를 위해 max 빼기)
        val maxLog = logPosteriors.values.max()
        val expValues = logPosteriors.mapValues { (_, v) -> Math.exp(v - maxLog) }
        val sumExp = expValues.values.sum()
        val probabilities = expValues.mapValues { (_, v) -> v / sumExp }

        val dominantFeatures = featureContributions
            .map { (name, ratio) -> FeatureContribution(name, ratio) }
            .sortedByDescending { abs(it.likelihoodRatio - 1.0) }

        return BayesResult(
            upProbability = probabilities[Label.UP] ?: 1.0 / 3,
            downProbability = probabilities[Label.DOWN] ?: 1.0 / 3,
            sidewaysProbability = probabilities[Label.SIDEWAYS] ?: 1.0 / 3,
            dominantFeatures = dominantFeatures,
            sampleCount = totalSamples
        )
    }

    private fun classifyReturn(returnRate: Double): Label = when {
        returnRate > UP_THRESHOLD -> Label.UP
        returnRate < DOWN_THRESHOLD -> Label.DOWN
        else -> Label.SIDEWAYS
    }

    /**
     * 지표 이산화: 각 날짜의 지표를 이산 상태 문자열로 변환
     */
    private fun extractFeatures(
        date: String,
        oscByDate: Map<String, OscillatorRow>,
        demarkByDate: Map<String, DemarkTDRow>,
        fundByDate: Map<String, FundamentalSnapshot>,
        volumeChanges: Map<String, Double>
    ): Map<String, String>? {
        val osc = oscByDate[date] ?: return null
        val features = mutableMapOf<String, String>()

        // MACD histogram 상태
        features["MACD"] = when {
            osc.oscillator > 0.00001 -> MacdState.POSITIVE.name
            osc.oscillator < -0.00001 -> MacdState.NEGATIVE.name
            else -> MacdState.NEAR_ZERO.name
        }

        // 수급 오실레이터 상태 (MACD 기반)
        features["OSCILLATOR"] = when {
            osc.macd > 0 -> OscillatorState.BUY.name
            osc.macd < 0 -> OscillatorState.SELL.name
            else -> OscillatorState.NEUTRAL.name
        }

        // EMA 배열 상태
        val emaSpread = osc.ema12 - osc.ema26
        features["EMA"] = when {
            emaSpread > 0.000001 -> EmaState.GOLDEN.name
            emaSpread < -0.000001 -> EmaState.DEAD.name
            else -> EmaState.CONVERGING.name
        }

        // DeMark 상태
        val demark = demarkByDate[date]
        features["DEMARK"] = when {
            demark != null && demark.tdBuyCount >= 7 -> DemarkState.SETUP_HIGH.name
            demark != null && demark.tdSellCount >= 7 -> DemarkState.SETUP_HIGH.name
            demark != null && (demark.tdBuyCount in 1..3 || demark.tdSellCount in 1..3) ->
                DemarkState.SETUP_LOW.name
            else -> DemarkState.NONE.name
        }

        // 거래량 상태 (시가총액 변화율 기반)
        val volChange = volumeChanges[date]
        features["VOLUME"] = when {
            volChange != null && volChange > 1.5 -> VolumeState.SURGE.name
            volChange != null && volChange < 0.7 -> VolumeState.LOW.name
            else -> VolumeState.NORMAL.name
        }

        // PBR 상태
        val fund = fundByDate[date]
        features["PBR"] = when {
            fund != null && fund.pbr < 1.0 -> PbrState.UNDERVALUED.name
            fund != null && fund.pbr in 1.0..2.0 -> PbrState.FAIR.name
            fund != null && fund.pbr > 2.0 -> PbrState.OVERVALUED.name
            else -> PbrState.FAIR.name // 데이터 없으면 기본값
        }

        return features
    }

    /**
     * 시가총액 변화율 계산 (거래량 프록시)
     * 각 날짜별로 현재 시가총액 / 20일 평균 시가총액 비율 반환
     */
    private fun calcVolumeChanges(prices: List<DailyTrading>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (i in prices.indices) {
            val start = maxOf(0, i - VOLUME_AVG_WINDOW + 1)
            val window = prices.subList(start, i + 1)
            val avgMarketCap = window.map { it.marketCap.toDouble() }.average()
            if (avgMarketCap > 0) {
                result[prices[i].date] = prices[i].marketCap.toDouble() / avgMarketCap
            }
        }
        return result
    }
}
