package com.tinyoscillator.data.engine

import android.content.SharedPreferences
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.LogisticResult
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import com.tinyoscillator.core.di.LogisticPrefs
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 로지스틱 회귀 스코어링 엔진
 *
 * P(up) = σ(Σ wᵢxᵢ + b) — sigmoid 함수로 상승 확률 산출.
 * 지표를 0~1로 정규화(min-max scaling) 후 가중 합산.
 * 경사하강법으로 학습, SharedPreferences에 가중치 저장.
 */
@Singleton
class LogisticScoringEngine @Inject constructor(
    @LogisticPrefs private val prefs: SharedPreferences
) {

    companion object {
        private const val LOOK_AHEAD_DAYS = 20
        private const val LEARNING_RATE = 0.01
        private const val EPOCHS = 100
        private const val CONVERGENCE_THRESHOLD = 1e-6
        private const val NORMALIZATION_WINDOW = 60
        private const val PREFS_PREFIX = "logistic_weight_"
        private const val PREFS_BIAS = "logistic_bias"
        private const val PREFS_TRAINED = "logistic_trained"

        val FEATURE_NAMES = listOf(
            "macd_histogram",
            "oscillator_value",
            "ema_spread",
            "volume_ratio",
            "demark_buy_setup",
            "pbr_inverse"
        )
    }

    /**
     * 예측 실행 — 저장된 가중치로 추론
     * 가중치가 없으면 학습 후 예측
     */
    suspend fun analyze(
        prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>,
        fundamentals: List<FundamentalSnapshot>?,
        demarkBuySetup: Int = 0
    ): LogisticResult {
        val oscByDate = oscillators.associateBy { it.date }
        val fundByDate = fundamentals?.associateBy { it.date } ?: emptyMap()

        // 학습이 안 되어 있으면 먼저 학습
        if (!prefs.getBoolean(PREFS_TRAINED, false)) {
            trainWeights(prices, oscillators, fundamentals)
        }

        // 저장된 가중치 로드
        val weights = loadWeights()
        val bias = prefs.getFloat(PREFS_BIAS, 0f).toDouble()

        // 현재 시점 피처 추출
        val currentDate = prices.last().date
        val currentOsc = oscByDate[currentDate] ?: oscillators.lastOrNull()
            ?: return defaultResult()

        val rawFeatures = extractRawFeatures(currentOsc, fundByDate[currentDate], demarkBuySetup)

        // 정규화 (최근 60일 데이터 기반 min-max)
        val recentOscillators = oscillators.takeLast(NORMALIZATION_WINDOW)
        val normalizedFeatures = normalizeFeatures(rawFeatures, recentOscillators, fundamentals)

        // 예측
        val z = dotProduct(weights, normalizedFeatures) + bias
        val probability = sigmoid(z)

        val featureValues = FEATURE_NAMES.zip(normalizedFeatures.toList()).toMap()
        val weightMap = FEATURE_NAMES.zip(weights).toMap()

        return LogisticResult(
            probability = probability,
            weights = weightMap,
            featureValues = featureValues,
            score0to100 = (probability * 100).roundToInt().coerceIn(0, 100)
        )
    }

    /**
     * 가중치 학습 — 경사하강법 (binary cross-entropy loss)
     */
    suspend fun trainWeights(
        prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>,
        fundamentals: List<FundamentalSnapshot>?
    ) {
        if (prices.size < LOOK_AHEAD_DAYS + NORMALIZATION_WINDOW) return

        val oscByDate = oscillators.associateBy { it.date }
        val fundByDate = fundamentals?.associateBy { it.date } ?: emptyMap()

        // 날짜→인덱스 맵 (이미 날짜순 정렬된 리스트에서 인덱스 기반 윈도우용)
        val oscDateIndex = mutableMapOf<String, Int>()
        oscillators.forEachIndexed { idx, o -> oscDateIndex[o.date] = idx }
        val fundDateIndex = mutableMapOf<String, Int>()
        fundamentals?.forEachIndexed { idx, f -> fundDateIndex[f.date] = idx }

        // 학습 데이터 생성
        val features = mutableListOf<DoubleArray>()
        val labels = mutableListOf<Double>()

        for (i in NORMALIZATION_WINDOW until prices.size - LOOK_AHEAD_DAYS) {
            val date = prices[i].date
            val osc = oscByDate[date] ?: continue
            val fund = fundByDate[date]

            val rawFeatures = extractRawFeatures(osc, fund, 0)
            val oscIdx = oscDateIndex[date] ?: continue
            val oscStart = max(0, oscIdx - NORMALIZATION_WINDOW + 1)
            val recentOsc = oscillators.subList(oscStart, oscIdx + 1)
            val recentFund = fundamentals?.let { fl ->
                val fIdx = fundDateIndex[date]
                if (fIdx != null) {
                    val fStart = max(0, fIdx - NORMALIZATION_WINDOW + 1)
                    fl.subList(fStart, fIdx + 1)
                } else null
            }
            val normalized = normalizeFeatures(rawFeatures, recentOsc, recentFund)
            features.add(normalized)

            val futurePrice = prices[i + LOOK_AHEAD_DAYS].closePrice
            val currentPrice = prices[i].closePrice
            val label = if (currentPrice > 0 && futurePrice > currentPrice) 1.0 else 0.0
            labels.add(label)
        }

        if (features.isEmpty()) return

        // 경사하강법 (수렴 시 조기 종료)
        val numFeatures = FEATURE_NAMES.size
        val weights = DoubleArray(numFeatures) { 0.0 }
        var bias = 0.0
        var prevLoss = Double.MAX_VALUE

        for (epoch in 0 until EPOCHS) {
            val gradW = DoubleArray(numFeatures) { 0.0 }
            var gradB = 0.0
            var totalLoss = 0.0

            for (j in features.indices) {
                val z = dotProduct(weights.toList(), features[j]) + bias
                val pred = sigmoid(z)
                val error = pred - labels[j]

                // Binary cross-entropy loss
                val clampedPred = pred.coerceIn(1e-15, 1.0 - 1e-15)
                totalLoss += -(labels[j] * ln(clampedPred) + (1 - labels[j]) * ln(1 - clampedPred))

                for (k in 0 until numFeatures) {
                    gradW[k] += error * features[j][k]
                }
                gradB += error
            }

            val n = features.size.toDouble()
            val avgLoss = totalLoss / n

            for (k in 0 until numFeatures) {
                weights[k] -= LEARNING_RATE * gradW[k] / n
            }
            bias -= LEARNING_RATE * gradB / n

            // 수렴 검사: loss 변화가 임계값 이하이면 조기 종료
            if (abs(prevLoss - avgLoss) < CONVERGENCE_THRESHOLD) {
                Timber.d("LogisticScoring 수렴: epoch=%d, loss=%.6f", epoch, avgLoss)
                break
            }
            prevLoss = avgLoss
        }

        // 가중치 저장
        saveWeights(weights.toList(), bias)
    }

    fun sigmoid(z: Double): Double = 1.0 / (1.0 + exp(-z.coerceIn(-500.0, 500.0)))

    private fun extractRawFeatures(
        osc: OscillatorRow,
        fund: FundamentalSnapshot?,
        demarkBuySetup: Int
    ): DoubleArray {
        return doubleArrayOf(
            osc.oscillator,                          // macd_histogram (oscillator = macd - signal)
            osc.macd,                                // oscillator_value
            osc.ema12 - osc.ema26,                   // ema_spread
            if (osc.supplyRatio != 0.0) 1.0 else 0.0, // volume_ratio proxy
            demarkBuySetup / 9.0,                    // demark_buy_setup normalized
            if (fund != null && fund.pbr > 0) 1.0 / fund.pbr else 0.5 // pbr_inverse
        )
    }

    private fun normalizeFeatures(
        rawFeatures: DoubleArray,
        recentOscillators: List<OscillatorRow>,
        fundamentals: List<FundamentalSnapshot>?
    ): DoubleArray {
        if (recentOscillators.isEmpty()) return rawFeatures

        // 각 피처별 min-max 범위 계산
        val oscValues = recentOscillators.map { it.oscillator }
        val macdValues = recentOscillators.map { it.macd }
        val emaSpreadValues = recentOscillators.map { it.ema12 - it.ema26 }

        return doubleArrayOf(
            minMaxNormalize(rawFeatures[0], oscValues),
            minMaxNormalize(rawFeatures[1], macdValues),
            minMaxNormalize(rawFeatures[2], emaSpreadValues),
            rawFeatures[3].coerceIn(0.0, 1.0),
            rawFeatures[4].coerceIn(0.0, 1.0),
            rawFeatures[5].coerceIn(0.0, 1.0)
        )
    }

    private fun minMaxNormalize(value: Double, history: List<Double>): Double {
        val minVal = history.min()
        val maxVal = history.max()
        val range = maxVal - minVal
        return if (range > 0) ((value - minVal) / range).coerceIn(0.0, 1.0) else 0.5
    }

    private fun dotProduct(weights: List<Double>, features: DoubleArray): Double {
        var sum = 0.0
        for (i in weights.indices) {
            if (i < features.size) sum += weights[i] * features[i]
        }
        return sum
    }

    private fun saveWeights(weights: List<Double>, bias: Double) {
        val editor = prefs.edit()
        weights.forEachIndexed { i, w ->
            editor.putFloat("$PREFS_PREFIX$i", w.toFloat())
        }
        editor.putFloat(PREFS_BIAS, bias.toFloat())
        editor.putBoolean(PREFS_TRAINED, true)
        editor.apply()
    }

    private fun loadWeights(): List<Double> {
        return FEATURE_NAMES.indices.map { i ->
            prefs.getFloat("$PREFS_PREFIX$i", 0f).toDouble()
        }
    }

    private fun defaultResult() = LogisticResult(
        probability = 0.5,
        weights = FEATURE_NAMES.associateWith { 0.0 },
        featureValues = FEATURE_NAMES.associateWith { 0.0 },
        score0to100 = 50
    )
}
