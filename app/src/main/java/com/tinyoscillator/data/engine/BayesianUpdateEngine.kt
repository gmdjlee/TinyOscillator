package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.BayesianUpdateResult
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.model.ProbabilityUpdate
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 베이지안 갱신 엔진 — 새 신호 도착 시 Bayes' theorem으로 상승 확률 갱신
 *
 * Posterior = (Prior × Likelihood) / Evidence
 *
 * 1. 초기 Prior: 과거 전체 데이터의 상승 비율 (base rate)
 * 2. 각 지표 신호별 Likelihood 테이블 (DB에서 사전 집계)
 * 3. 신호가 순차적으로 도착할 때마다 posterior 갱신
 * 4. 갱신 히스토리 추적
 */
@Singleton
class BayesianUpdateEngine @Inject constructor() {

    companion object {
        private const val LOOK_AHEAD_DAYS = 20
        private const val UP_THRESHOLD = 0.0 // 단순 양수 수익 = 상승
    }

    /**
     * 베이지안 갱신 실행
     *
     * @param prices 일별 거래 데이터 (날짜 오름차순)
     * @param oscillators 오실레이터 데이터
     * @param demarkRows DeMark TD 데이터
     * @param fundamentals 펀더멘털 데이터
     */
    suspend fun analyze(
        prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>,
        demarkRows: List<DemarkTDRow>,
        fundamentals: List<FundamentalSnapshot>?
    ): BayesianUpdateResult {
        if (prices.size < LOOK_AHEAD_DAYS + 10) {
            return BayesianUpdateResult(0.5, 0.5, emptyList())
        }

        // Step 1: Base rate (사전 확률) 계산
        val priorProbability = calcBaseRate(prices)

        // Step 2: Likelihood 테이블 구축 (과거 데이터에서)
        val likelihoodTable = buildLikelihoodTable(prices, oscillators, demarkRows, fundamentals)

        // Step 3: 현재 시점의 신호 추출
        val currentSignals = extractCurrentSignals(oscillators, demarkRows, fundamentals)

        // Step 4: 순차적 베이지안 갱신
        var posterior = priorProbability
        val updateHistory = mutableListOf<ProbabilityUpdate>()

        for ((signalName, signalValue) in currentSignals) {
            val likelihood = likelihoodTable[signalName] ?: continue
            val pSignalGivenUp = likelihood.pGivenUp(signalValue)
            val pSignalGivenDown = likelihood.pGivenDown(signalValue)

            val beforeProb = posterior

            // Bayes update: P(UP|signal) = P(signal|UP) * P(UP) / P(signal)
            val pSignal = pSignalGivenUp * posterior + pSignalGivenDown * (1.0 - posterior)
            if (pSignal > 1e-10) {
                posterior = (pSignalGivenUp * posterior) / pSignal
            }

            // 안정성: 0~1 범위 강제
            posterior = posterior.coerceIn(0.001, 0.999)

            val likelihoodRatio = if (pSignalGivenDown > 1e-10)
                pSignalGivenUp / pSignalGivenDown else 1.0

            updateHistory.add(ProbabilityUpdate(
                signalName = "$signalName=$signalValue",
                beforeProb = beforeProb,
                afterProb = posterior,
                deltaProb = posterior - beforeProb,
                likelihoodRatio = likelihoodRatio
            ))
        }

        return BayesianUpdateResult(
            finalPosterior = posterior,
            priorProbability = priorProbability,
            updateHistory = updateHistory
        )
    }

    /**
     * Base rate 계산: 과거 데이터에서 20일 후 상승한 비율
     */
    private fun calcBaseRate(prices: List<DailyTrading>): Double {
        var upCount = 0
        var totalCount = 0

        for (i in 0 until prices.size - LOOK_AHEAD_DAYS) {
            val currentPrice = prices[i].closePrice
            val futurePrice = prices[i + LOOK_AHEAD_DAYS].closePrice
            if (currentPrice > 0) {
                totalCount++
                if (futurePrice > currentPrice) upCount++
            }
        }

        return if (totalCount > 0) upCount.toDouble() / totalCount else 0.5
    }

    /**
     * Likelihood 테이블 구축
     * 각 신호별 P(signal_value | UP)과 P(signal_value | DOWN) 집계
     */
    private fun buildLikelihoodTable(
        prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>,
        demarkRows: List<DemarkTDRow>,
        fundamentals: List<FundamentalSnapshot>?
    ): Map<String, LikelihoodEntry> {
        val oscByDate = oscillators.associateBy { it.date }
        val demarkByDate = demarkRows.associateBy { it.date }
        val fundByDate = fundamentals?.associateBy { it.date } ?: emptyMap()

        // 신호별 (UP에서의 카운트, DOWN에서의 카운트)
        val countsUp = mutableMapOf<String, MutableMap<String, Int>>()
        val countsDown = mutableMapOf<String, MutableMap<String, Int>>()
        var totalUp = 0
        var totalDown = 0

        for (i in 0 until prices.size - LOOK_AHEAD_DAYS) {
            val date = prices[i].date
            val currentPrice = prices[i].closePrice
            val futurePrice = prices[i + LOOK_AHEAD_DAYS].closePrice
            if (currentPrice <= 0) continue

            val isUp = futurePrice > currentPrice
            if (isUp) totalUp++ else totalDown++

            val signals = extractSignalsAtDate(date, oscByDate, demarkByDate, fundByDate)
            val targetMap = if (isUp) countsUp else countsDown

            for ((name, value) in signals) {
                targetMap.getOrPut(name) { mutableMapOf() }
                    .merge(value, 1) { a, b -> a + b }
            }
        }

        // Likelihood 엔트리 생성 (Laplace smoothing 포함)
        val allSignalNames = (countsUp.keys + countsDown.keys).toSet()
        val result = mutableMapOf<String, LikelihoodEntry>()

        for (signalName in allSignalNames) {
            val upCounts = countsUp[signalName] ?: emptyMap()
            val downCounts = countsDown[signalName] ?: emptyMap()
            val allValues = (upCounts.keys + downCounts.keys).toSet()

            result[signalName] = LikelihoodEntry(
                upCounts = upCounts,
                downCounts = downCounts,
                totalUp = totalUp,
                totalDown = totalDown,
                numValues = allValues.size
            )
        }

        return result
    }

    /**
     * 현재 시점의 신호 추출
     */
    private fun extractCurrentSignals(
        oscillators: List<OscillatorRow>,
        demarkRows: List<DemarkTDRow>,
        fundamentals: List<FundamentalSnapshot>?
    ): List<Pair<String, String>> {
        val signals = mutableListOf<Pair<String, String>>()

        oscillators.lastOrNull()?.let { osc ->
            signals.add("MACD" to if (osc.oscillator > 0) "POSITIVE" else "NEGATIVE")
            signals.add("수급" to if (osc.macd > 0) "BUY" else "SELL")
            signals.add("EMA" to if (osc.ema12 > osc.ema26) "GOLDEN" else "DEAD")
        }

        demarkRows.lastOrNull()?.let { d ->
            val state = when {
                d.tdBuyCount >= 7 -> "HIGH"
                d.tdSellCount >= 7 -> "HIGH"
                else -> "NONE"
            }
            signals.add("DeMark" to state)
        }

        fundamentals?.lastOrNull()?.let { f ->
            val pbrState = when {
                f.pbr > 0 && f.pbr < 1.0 -> "UNDERVALUED"
                f.pbr in 1.0..2.0 -> "FAIR"
                else -> "OVERVALUED"
            }
            signals.add("PBR" to pbrState)
        }

        return signals
    }

    /**
     * 특정 날짜의 신호 추출 (학습용)
     */
    private fun extractSignalsAtDate(
        date: String,
        oscByDate: Map<String, OscillatorRow>,
        demarkByDate: Map<String, DemarkTDRow>,
        fundByDate: Map<String, FundamentalSnapshot>
    ): List<Pair<String, String>> {
        val signals = mutableListOf<Pair<String, String>>()

        oscByDate[date]?.let { osc ->
            signals.add("MACD" to if (osc.oscillator > 0) "POSITIVE" else "NEGATIVE")
            signals.add("수급" to if (osc.macd > 0) "BUY" else "SELL")
            signals.add("EMA" to if (osc.ema12 > osc.ema26) "GOLDEN" else "DEAD")
        }

        demarkByDate[date]?.let { d ->
            val state = when {
                d.tdBuyCount >= 7 -> "HIGH"
                d.tdSellCount >= 7 -> "HIGH"
                else -> "NONE"
            }
            signals.add("DeMark" to state)
        }

        fundByDate[date]?.let { f ->
            val pbrState = when {
                f.pbr > 0 && f.pbr < 1.0 -> "UNDERVALUED"
                f.pbr in 1.0..2.0 -> "FAIR"
                else -> "OVERVALUED"
            }
            signals.add("PBR" to pbrState)
        }

        return signals
    }

    /**
     * Likelihood 엔트리 — Laplace smoothing 적용
     */
    data class LikelihoodEntry(
        val upCounts: Map<String, Int>,
        val downCounts: Map<String, Int>,
        val totalUp: Int,
        val totalDown: Int,
        val numValues: Int
    ) {
        fun pGivenUp(value: String): Double {
            val count = upCounts[value] ?: 0
            return (count + 1.0) / (totalUp + numValues) // Laplace
        }

        fun pGivenDown(value: String): Double {
            val count = downCounts[value] ?: 0
            return (count + 1.0) / (totalDown + numValues) // Laplace
        }
    }
}
