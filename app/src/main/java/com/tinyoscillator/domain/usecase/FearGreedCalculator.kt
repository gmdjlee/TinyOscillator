package com.tinyoscillator.domain.usecase

/**
 * Fear & Greed Calculator — Pure Kotlin Computations
 *
 * Python fear & greed 분석 알고리즘의 Kotlin 포트:
 * - RSI(window=10): 단순 이동평균 기반 (Wilder EMA 아님)
 * - MACD(12,26,9): pandas ewm(adjust=False) 호환 EMA
 * - MinMax 정규화: 유효 행만을 범위로 사용
 * - FG = Mom*0.2 + (1-PCR)*0.2 + (1-Vol)*0.2 + Spread*0.2 + RSI*0.2
 *
 * 순수 object — DI, 코루틴, 외부 의존성 없음.
 * Double.NaN으로 pandas NaN 동작을 재현한다.
 */
object FearGreedCalculator {

    // ============================================================
    // Input / Output data models
    // ============================================================

    /**
     * 하루치 병합 입력 데이터.
     *
     * @param date        거래일 (yyyy-MM-dd)
     * @param indexValue  KOSPI 또는 KOSDAQ 종가
     * @param call        콜옵션 거래량 5일 이동평균
     * @param put         풋옵션 거래량 5일 이동평균
     * @param vix         VKOSPI 종가
     * @param bond5y      5년 국채 금리
     * @param bond10y     10년 국채 금리
     */
    data class FearGreedDayData(
        val date: String,
        val indexValue: Double,
        val call: Double,
        val put: Double,
        val vix: Double,
        val bond5y: Double,
        val bond10y: Double
    )

    /**
     * 일별 Fear & Greed 분석 결과.
     * 모든 구성요소 값은 MinMax 정규화 완료 상태 [0, 1].
     */
    data class FearGreedResult(
        val date: String,
        val indexValue: Double,
        val fearGreedValue: Double,
        val oscillator: Double,
        val rsi: Double,
        val momentum: Double,
        val putCallRatio: Double,
        val volatility: Double,
        val spread: Double
    )

    // ============================================================
    // Public API
    // ============================================================

    /**
     * 옵션 거래량의 5일 이동평균.
     * pandas Series.rolling(5).mean() 호환.
     * 처음 4개는 NaN.
     */
    fun rollingMean5(values: List<Long>): List<Double> {
        if (values.isEmpty()) return emptyList()
        val period = 5
        val result = mutableListOf<Double>()
        var runningSum = 0L
        for (i in values.indices) {
            runningSum += values[i]
            if (i < period - 1) {
                result.add(Double.NaN)
            } else {
                if (i >= period) {
                    runningSum -= values[i - period]
                }
                result.add(runningSum.toDouble() / period)
            }
        }
        return result
    }

    /**
     * RSI 계산 (window=10, 단순 이동평균 기반).
     * 처음 window개는 NaN.
     */
    fun calcRsi(series: List<Double>, window: Int = 10): List<Double> {
        if (series.size < 2) return List(series.size) { Double.NaN }

        val n = series.size
        val gain = DoubleArray(n) { Double.NaN }
        val loss = DoubleArray(n) { Double.NaN }
        for (i in 1 until n) {
            val d = series[i] - series[i - 1]
            gain[i] = if (d > 0.0) d else 0.0
            loss[i] = if (d < 0.0) -d else 0.0
        }

        val result = mutableListOf<Double>()
        for (i in 0 until n) {
            if (i < window) {
                result.add(Double.NaN)
                continue
            }
            val startIdx = i - window + 1
            var sumGain = 0.0
            var sumLoss = 0.0
            for (j in startIdx..i) {
                sumGain += gain[j]
                sumLoss += loss[j]
            }
            val avgGain = sumGain / window
            val avgLoss = sumLoss / window

            val rsi = if (avgLoss == 0.0) {
                Double.NaN
            } else {
                val rs = avgGain / avgLoss
                100.0 - (100.0 / (1.0 + rs))
            }
            result.add(rsi)
        }
        return result
    }

    /**
     * MACD 오실레이터 (히스토그램).
     * EMA: pandas ewm(span=n, adjust=False) 호환 — alpha = 2/(n+1).
     */
    fun calcMacd(
        series: List<Double>,
        short: Int = 12,
        long: Int = 26,
        sig: Int = 9
    ): List<Double> {
        if (series.isEmpty()) return emptyList()
        val emaShort = calculateEma(series, short)
        val emaLong = calculateEma(series, long)
        val macd = emaShort.zip(emaLong) { s, l -> s - l }
        val signal = calculateEma(macd, sig)
        return macd.zip(signal) { m, s -> m - s }
    }

    /**
     * Fear & Greed 지수 계산 메인 파이프라인.
     *
     * 1. 적응형 MA 기간: min(125, max(10, floor(n * 0.9)))
     * 2. 5개 피처 계산: Momentum, PCR, Volatility, Spread, RSI
     * 3. 유효 행만으로 MinMax 정규화
     * 4. FG = 가중평균 (각 0.2)
     * 5. MACD 오실레이터 계산
     *
     * NaN 행은 호출자가 isFinite()로 필터링해야 한다.
     */
    fun calcFearGreed(data: List<FearGreedDayData>): List<FearGreedResult> {
        val n = data.size
        if (n == 0) return emptyList()

        // Step 1: 적응형 MA 기간
        val maPeriod = minOf(125, maxOf(10, (n * 0.9).toInt()))

        // Step 2: 지수 이동평균
        val indexValues = data.map { it.indexValue }
        val ma = rollingSimpleMean(indexValues, maPeriod)

        // Step 3: 원시 피처 계산
        val rsiSeries = calcRsi(indexValues, window = 10)
        val mom = DoubleArray(n)
        val pcr = DoubleArray(n)
        val vol = DoubleArray(n)
        val spread = DoubleArray(n)
        val rsi = DoubleArray(n)

        for (i in 0 until n) {
            val maVal = ma[i]
            mom[i] = if (maVal.isFinite() && maVal != 0.0) {
                (indexValues[i] - maVal) / maVal * 100.0
            } else Double.NaN

            val callVal = data[i].call
            val putVal = data[i].put
            pcr[i] = if (callVal.isFinite() && callVal != 0.0 && putVal.isFinite()) {
                putVal / callVal
            } else Double.NaN

            vol[i] = data[i].vix
            spread[i] = data[i].bond10y - data[i].bond5y
            rsi[i] = rsiSeries[i]
        }

        // Step 4: 유효 행 판정 — 5개 피처 모두 finite
        val valid = BooleanArray(n) { i ->
            mom[i].isFinite() && pcr[i].isFinite() && vol[i].isFinite() &&
                    spread[i].isFinite() && rsi[i].isFinite()
        }

        // Step 5: MinMax 정규화
        val normMom = minMaxNormalize(mom, valid)
        val normPcr = minMaxNormalize(pcr, valid)
        val normVol = minMaxNormalize(vol, valid)
        val normSpread = minMaxNormalize(spread, valid)
        val normRsi = minMaxNormalize(rsi, valid)

        // Step 6: FG 점수 계산
        val fg = DoubleArray(n) { i ->
            if (!valid[i]) Double.NaN
            else normMom[i] * 0.2 + (1.0 - normPcr[i]) * 0.2 +
                    (1.0 - normVol[i]) * 0.2 + normSpread[i] * 0.2 + normRsi[i] * 0.2
        }

        // Step 7: MACD 오실레이터 (NaN 전파)
        val osc = calcMacdNullAware(fg.toList())

        // Step 8: 결과 조립
        return data.indices.map { i ->
            FearGreedResult(
                date = data[i].date,
                indexValue = data[i].indexValue,
                fearGreedValue = fg[i],
                oscillator = osc[i],
                rsi = normRsi[i],
                momentum = normMom[i],
                putCallRatio = normPcr[i],
                volatility = normVol[i],
                spread = normSpread[i]
            )
        }
    }

    // ============================================================
    // Internal helpers
    // ============================================================

    /** EMA — pandas ewm(span=period, adjust=False) 호환. */
    internal fun calculateEma(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val alpha = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema = values[0]
        result.add(ema)
        for (i in 1 until values.size) {
            ema = alpha * values[i] + (1.0 - alpha) * ema
            result.add(ema)
        }
        return result
    }

    /** NaN 전파 MACD — FG 시리즈에 NaN 행이 포함된 경우 사용. */
    private fun calcMacdNullAware(
        series: List<Double>,
        short: Int = 12,
        long: Int = 26,
        sig: Int = 9
    ): List<Double> {
        val n = series.size
        if (n == 0) return emptyList()
        val emaShort = emaWithNan(series, short)
        val emaLong = emaWithNan(series, long)
        val macd = DoubleArray(n) { i ->
            if (emaShort[i].isNaN() || emaLong[i].isNaN()) Double.NaN
            else emaShort[i] - emaLong[i]
        }
        val signal = emaWithNan(macd.toList(), sig)
        return List(n) { i ->
            if (macd[i].isNaN() || signal[i].isNaN()) Double.NaN
            else macd[i] - signal[i]
        }
    }

    /** NaN 전파 EMA — NaN 입력 시 상태를 동결하고 NaN 출력. */
    private fun emaWithNan(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val alpha = 2.0 / (period + 1)
        val result = mutableListOf<Double>()
        var ema: Double? = null
        for (v in values) {
            if (!v.isFinite()) {
                result.add(Double.NaN)
            } else {
                if (ema == null) {
                    ema = v
                } else {
                    ema = alpha * v + (1.0 - alpha) * ema
                }
                result.add(ema)
            }
        }
        return result
    }

    /** 단순 이동평균 (pandas rolling().mean() 호환). */
    private fun rollingSimpleMean(values: List<Double>, period: Int): List<Double> {
        val n = values.size
        if (n == 0) return emptyList()
        val result = mutableListOf<Double>()
        var runningSum = 0.0
        for (i in 0 until n) {
            runningSum += values[i]
            if (i < period - 1) {
                result.add(Double.NaN)
            } else {
                if (i >= period) {
                    runningSum -= values[i - period]
                }
                result.add(runningSum / period)
            }
        }
        return result
    }

    /** MinMax 정규화 — valid 행만을 범위로 사용. */
    private fun minMaxNormalize(values: DoubleArray, valid: BooleanArray): DoubleArray {
        val n = values.size
        if (n == 0) return DoubleArray(0)

        var colMin = Double.MAX_VALUE
        var colMax = -Double.MAX_VALUE
        for (i in 0 until n) {
            if (valid[i]) {
                if (values[i] < colMin) colMin = values[i]
                if (values[i] > colMax) colMax = values[i]
            }
        }

        val hasValidRows = colMin != Double.MAX_VALUE
        val result = DoubleArray(n) { Double.NaN }
        val range = colMax - colMin

        for (i in 0 until n) {
            if (!valid[i]) {
                result[i] = Double.NaN
                continue
            }
            result[i] = if (hasValidRows && range > 0.0) {
                (values[i] - colMin) / range
            } else {
                0.0
            }
        }
        return result
    }
}
