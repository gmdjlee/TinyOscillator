package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.CorrelationAnalysis
import com.tinyoscillator.domain.model.CorrelationResult
import com.tinyoscillator.domain.model.CorrelationStrength
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.LeadLagResult
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.SectorEtfReturn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 상관/선행-후행 분석 엔진
 *
 * A) 지표 간 상관 분석 (Pearson r, 60일 롤링)
 * B) 선행-후행 분석 (cross-correlation, lag -5 ~ +5)
 *
 * 분석 쌍:
 * - 수급 오실레이터 ↔ MACD histogram
 * - 수급 오실레이터 ↔ 거래량 변화율
 * - MACD ↔ EMA spread
 * - Demark count ↔ 수급 오실레이터
 * - 개별종목 수익률 ↔ 섹터 ETF 수익률
 */
@Singleton
class CorrelationEngine @Inject constructor() {

    companion object {
        private const val ROLLING_WINDOW = 60
        private const val MAX_LAG = 5
    }

    /**
     * 상관 분석 실행
     *
     * @param oscillators 오실레이터 데이터 (날짜 오름차순)
     * @param demarkRows DeMark TD 데이터
     * @param prices 일별 거래 데이터
     * @param sectorEtfReturns 섹터 ETF 수익률 (nullable)
     */
    suspend fun analyze(
        oscillators: List<OscillatorRow>,
        demarkRows: List<DemarkTDRow>,
        prices: List<DailyTrading>,
        sectorEtfReturns: List<SectorEtfReturn>? = null
    ): CorrelationAnalysis {
        if (oscillators.size < 10) {
            return CorrelationAnalysis(emptyList(), emptyList())
        }

        val correlations = mutableListOf<CorrelationResult>()
        val leadLagResults = mutableListOf<LeadLagResult>()

        // 시계열 데이터 추출 (최근 ROLLING_WINDOW 사용)
        val recentOsc = oscillators.takeLast(ROLLING_WINDOW)
        val oscDates = recentOsc.map { it.date }.toSet()

        val macdHistogram = recentOsc.map { it.oscillator }
        val supplyMacd = recentOsc.map { it.macd }
        val emaSpread = recentOsc.map { it.ema12 - it.ema26 }
        val supplyRatio = recentOsc.map { it.supplyRatio }

        // 거래량 변화율 (시가총액 기반)
        val volumeChanges = calcVolumeChanges(recentOsc)

        // DeMark count (날짜 매칭)
        val demarkByDate = demarkRows.associateBy { it.date }
        val demarkCounts = recentOsc.map { osc ->
            val d = demarkByDate[osc.date]
            (d?.tdBuyCount ?: 0) - (d?.tdSellCount ?: 0).toDouble()
        }

        // 개별종목 수익률
        val priceByDate = prices.associateBy { it.date }
        val stockReturns = calcDailyReturns(recentOsc.mapNotNull { osc ->
            priceByDate[osc.date]?.closePrice?.toDouble()
        })

        // ─── A) Pearson 상관 계산 ───

        // 1. 수급 오실레이터 ↔ MACD histogram
        addCorrelation(correlations, "수급오실레이터", "MACD히스토그램", supplyMacd, macdHistogram)

        // 2. 수급 오실레이터 ↔ 거래량 변화율
        if (volumeChanges.size == supplyMacd.size) {
            addCorrelation(correlations, "수급오실레이터", "거래량변화율", supplyMacd, volumeChanges)
        }

        // 3. MACD ↔ EMA spread
        addCorrelation(correlations, "MACD", "EMA스프레드", supplyMacd, emaSpread)

        // 4. Demark count ↔ 수급 오실레이터
        addCorrelation(correlations, "DeMark카운트", "수급오실레이터", demarkCounts, supplyMacd)

        // 5. 개별종목 수익률 ↔ 섹터 ETF 수익률
        if (sectorEtfReturns != null && sectorEtfReturns.isNotEmpty()) {
            val etfReturnByDate = sectorEtfReturns.associateBy { it.date }
            val matchedStockReturns = mutableListOf<Double>()
            val matchedEtfReturns = mutableListOf<Double>()

            for (i in 1 until recentOsc.size) {
                val date = recentOsc[i].date
                val prevPrice = priceByDate[recentOsc[i - 1].date]?.closePrice?.toDouble() ?: continue
                val currPrice = priceByDate[date]?.closePrice?.toDouble() ?: continue
                val etfReturn = etfReturnByDate[date]?.dailyReturn ?: continue
                if (prevPrice > 0) {
                    matchedStockReturns.add((currPrice - prevPrice) / prevPrice)
                    matchedEtfReturns.add(etfReturn)
                }
            }

            if (matchedStockReturns.size >= 10) {
                addCorrelation(correlations, "종목수익률", "섹터ETF수익률",
                    matchedStockReturns, matchedEtfReturns)
            }
        }

        // ─── B) 선행-후행 분석 (cross-correlation) ───

        addLeadLag(leadLagResults, "수급오실레이터", "MACD히스토그램", supplyMacd, macdHistogram)
        addLeadLag(leadLagResults, "MACD", "EMA스프레드", supplyMacd, emaSpread)
        addLeadLag(leadLagResults, "DeMark카운트", "수급오실레이터", demarkCounts, supplyMacd)

        return CorrelationAnalysis(
            correlations = correlations,
            leadLagResults = leadLagResults
        )
    }

    /**
     * Pearson 상관계수 계산
     */
    fun pearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        val n = minOf(x.size, y.size)
        if (n < 3) return 0.0

        val xSlice = x.takeLast(n)
        val ySlice = y.takeLast(n)

        val meanX = xSlice.average()
        val meanY = ySlice.average()

        var sumXY = 0.0
        var sumX2 = 0.0
        var sumY2 = 0.0

        for (i in 0 until n) {
            val dx = xSlice[i] - meanX
            val dy = ySlice[i] - meanY
            sumXY += dx * dy
            sumX2 += dx * dx
            sumY2 += dy * dy
        }

        val denominator = sqrt(sumX2 * sumY2)
        return if (denominator > 1e-10) (sumXY / denominator).coerceIn(-1.0, 1.0) else 0.0
    }

    /**
     * Cross-correlation: lag별로 한 시리즈를 shift하고 Pearson r 계산
     * 양의 lag: series2가 series1보다 lag일 후행
     * 음의 lag: series2가 series1보다 |lag|일 선행
     */
    fun crossCorrelation(series1: List<Double>, series2: List<Double>): Pair<Int, Double> {
        var bestLag = 0
        var bestR = 0.0

        for (lag in -MAX_LAG..MAX_LAG) {
            val (s1, s2) = alignWithLag(series1, series2, lag)
            if (s1.size < 10) continue
            val r = pearsonCorrelation(s1, s2)
            if (abs(r) > abs(bestR)) {
                bestR = r
                bestLag = lag
            }
        }

        return bestLag to bestR
    }

    private fun alignWithLag(
        series1: List<Double>, series2: List<Double>, lag: Int
    ): Pair<List<Double>, List<Double>> {
        val n = minOf(series1.size, series2.size)
        return if (lag >= 0) {
            val end = n - lag
            if (end <= 0) emptyList<Double>() to emptyList()
            else series1.subList(0, end) to series2.subList(lag, n)
        } else {
            val absLag = -lag
            val end = n - absLag
            if (end <= 0) emptyList<Double>() to emptyList()
            else series1.subList(absLag, n) to series2.subList(0, end)
        }
    }

    private fun addCorrelation(
        results: MutableList<CorrelationResult>,
        name1: String, name2: String,
        series1: List<Double>, series2: List<Double>
    ) {
        val r = pearsonCorrelation(series1, series2)
        results.add(CorrelationResult(
            indicator1 = name1,
            indicator2 = name2,
            pearsonR = r,
            strength = CorrelationStrength.fromR(r)
        ))
    }

    private fun addLeadLag(
        results: MutableList<LeadLagResult>,
        name1: String, name2: String,
        series1: List<Double>, series2: List<Double>
    ) {
        val (optimalLag, rAtLag) = crossCorrelation(series1, series2)
        val interpretation = when {
            optimalLag > 0 -> "${name1}이(가) ${name2}를 ${optimalLag}일 선행"
            optimalLag < 0 -> "${name2}이(가) ${name1}를 ${-optimalLag}일 선행"
            else -> "${name1}과 ${name2}는 동행"
        }
        results.add(LeadLagResult(
            indicator1 = name1,
            indicator2 = name2,
            optimalLag = optimalLag,
            rAtOptimalLag = rAtLag,
            interpretation = interpretation
        ))
    }

    private fun calcVolumeChanges(oscillators: List<OscillatorRow>): List<Double> {
        if (oscillators.size < 2) return emptyList()
        return oscillators.zipWithNext { prev, curr ->
            if (prev.supplyRatio != 0.0) curr.supplyRatio / prev.supplyRatio - 1.0 else 0.0
        }
    }

    private fun calcDailyReturns(prices: List<Double>): List<Double> {
        if (prices.size < 2) return emptyList()
        return prices.zipWithNext { prev, curr ->
            if (prev > 0) (curr - prev) / prev else 0.0
        }
    }
}
