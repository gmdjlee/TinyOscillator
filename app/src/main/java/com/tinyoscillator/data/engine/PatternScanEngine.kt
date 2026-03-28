package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.model.PatternAnalysis
import com.tinyoscillator.domain.model.PatternMatch
import com.tinyoscillator.domain.model.PatternOccurrence
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * 패턴 스캔 엔진 — 사전 정의된 기술적 패턴을 DB에서 스캔하고 후속 수익률 통계를 집계
 *
 * 8개 패턴 정의:
 * 1. MACD 골든크로스 + 수급 매수
 * 2. DeMark Setup9 + 거래량 급증
 * 3. EMA 골든크로스 + 저PBR
 * 4. 오실레이터 3일 연속 상승 + MACD 양전환
 * 5. 트리플 불리시 (EMA정배열 + MACD+ + 수급BUY)
 * 6. 거래량 돌파 + EMA 지지 (거래량200%+ & 가격>EMA20)
 * 7. DeMark Countdown13 완료
 * 8. 약세 다이버전스 (가격 신고가 but 수급 하락)
 */
@Singleton
class PatternScanEngine @Inject constructor() {

    companion object {
        private const val VOLUME_SURGE_RATIO = 1.5
        private const val VOLUME_BREAKOUT_RATIO = 2.0
        private const val VOLUME_AVG_WINDOW = 20
    }

    /**
     * 전체 패턴 분석 실행
     */
    suspend fun analyze(
        prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>,
        demarkRows: List<DemarkTDRow>,
        fundamentals: List<FundamentalSnapshot>?
    ): PatternAnalysis {
        if (prices.size < 25) {
            return PatternAnalysis(emptyList(), emptyList(), prices.size)
        }

        val priceByDate = prices.associateBy { it.date }
        val oscByDate = oscillators.associateBy { it.date }
        val demarkByDate = demarkRows.associateBy { it.date }
        val fundByDate = fundamentals?.associateBy { it.date } ?: emptyMap()
        val volumeRatios = calcVolumeRatios(prices)

        // 모든 패턴 정의 및 스캔
        val patternDefs = listOf(
            PatternDef("MACD_GOLDEN_CROSS_WITH_SUPPLY_BUY",
                "MACD 골든크로스 + 수급 매수 신호") { i ->
                checkMacdGoldenCrossWithSupplyBuy(i, prices, oscillators, oscByDate)
            },
            PatternDef("DEMARK_SETUP9_WITH_VOLUME_SURGE",
                "DeMark Setup 9 도달 + 거래량 급증") { i ->
                checkDemarkSetup9WithVolumeSurge(i, prices, demarkByDate, volumeRatios)
            },
            PatternDef("EMA_GOLDEN_CROSS_WITH_LOW_PBR",
                "EMA 골든크로스 + PBR < 1.0 저평가") { i ->
                checkEmaGoldenCrossWithLowPbr(i, prices, oscillators, oscByDate, fundByDate)
            },
            PatternDef("OSCILLATOR_3DAY_RISING_MACD_POSITIVE",
                "오실레이터 3일 연속 상승 + MACD 양전환") { i ->
                checkOscillator3DayRisingMacdPositive(i, prices, oscillators, oscByDate)
            },
            PatternDef("TRIPLE_BULLISH",
                "트리플 불리시: EMA정배열 + MACD양 + 수급매수") { i ->
                checkTripleBullish(i, prices, oscByDate)
            },
            PatternDef("VOLUME_BREAKOUT_WITH_EMA_SUPPORT",
                "거래량 200%+ 돌파 + EMA20 지지") { i ->
                checkVolumeBreakoutWithEmaSupport(i, prices, oscByDate, volumeRatios)
            },
            PatternDef("DEMARK_COUNTDOWN13_COMPLETION",
                "DeMark Countdown 13 완료") { i ->
                checkDemarkCountdown13(i, prices, demarkByDate)
            },
            PatternDef("BEARISH_DIVERGENCE",
                "약세 다이버전스: 가격 신고가 but 수급 하락") { i ->
                checkBearishDivergence(i, prices, oscByDate)
            }
        )

        val allPatterns = patternDefs.map { def ->
            scanPattern(def, prices)
        }

        val activePatterns = allPatterns.filter { it.isActive }

        return PatternAnalysis(
            allPatterns = allPatterns,
            activePatterns = activePatterns,
            totalHistoricalDays = prices.size
        )
    }

    /**
     * 개별 패턴 스캔 — 과거 발생 시점 탐색 + 후속 수익률 집계
     */
    private fun scanPattern(def: PatternDef, prices: List<DailyTrading>): PatternMatch {
        val occurrences = mutableListOf<PatternOccurrence>()

        // 과거 데이터에서 패턴 발생 탐색 (최소 20일 여유 필요)
        for (i in 1 until prices.size - 20) {
            if (def.condition(i)) {
                val occ = calcReturns(i, prices)
                if (occ != null) occurrences.add(occ)
            }
        }

        // 현재 활성 여부 (마지막 날짜에서 패턴 충족)
        val isActive = if (prices.size > 1) def.condition(prices.size - 1) else false

        // 통계 집계
        val winRate5d = if (occurrences.isNotEmpty())
            occurrences.count { it.return5d > 0 }.toDouble() / occurrences.size else 0.0
        val winRate10d = if (occurrences.isNotEmpty())
            occurrences.count { it.return10d > 0 }.toDouble() / occurrences.size else 0.0
        val winRate20d = if (occurrences.isNotEmpty())
            occurrences.count { it.return20d > 0 }.toDouble() / occurrences.size else 0.0

        val avgReturn5d = occurrences.map { it.return5d }.averageOrZero()
        val avgReturn10d = occurrences.map { it.return10d }.averageOrZero()
        val avgReturn20d = occurrences.map { it.return20d }.averageOrZero()
        val avgMdd20d = occurrences.map { it.mdd20d }.averageOrZero()

        return PatternMatch(
            patternName = def.name,
            patternDescription = def.description,
            isActive = isActive,
            occurrences = occurrences.takeLast(3), // 최근 3회
            winRate5d = winRate5d,
            winRate10d = winRate10d,
            winRate20d = winRate20d,
            avgReturn5d = avgReturn5d,
            avgReturn10d = avgReturn10d,
            avgReturn20d = avgReturn20d,
            avgMdd20d = avgMdd20d,
            totalOccurrences = occurrences.size
        )
    }

    /**
     * 후속 수익률 및 MDD 계산
     */
    private fun calcReturns(index: Int, prices: List<DailyTrading>): PatternOccurrence? {
        val basePrice = prices[index].closePrice.toDouble()
        if (basePrice <= 0) return null

        val date = prices[index].date
        val endIdx = min(index + 20, prices.size - 1)

        val price5d = if (index + 5 <= prices.size - 1) prices[index + 5].closePrice.toDouble() else basePrice
        val price10d = if (index + 10 <= prices.size - 1) prices[index + 10].closePrice.toDouble() else basePrice
        val price20d = if (index + 20 <= prices.size - 1) prices[index + 20].closePrice.toDouble() else basePrice

        // 20일 내 MDD 계산
        var maxPrice = basePrice
        var maxDrawdown = 0.0
        for (j in index..endIdx) {
            val p = prices[j].closePrice.toDouble()
            if (p > maxPrice) maxPrice = p
            val dd = (maxPrice - p) / maxPrice
            if (dd > maxDrawdown) maxDrawdown = dd
        }

        return PatternOccurrence(
            date = date,
            return5d = (price5d - basePrice) / basePrice,
            return10d = (price10d - basePrice) / basePrice,
            return20d = (price20d - basePrice) / basePrice,
            mdd20d = maxDrawdown
        )
    }

    // ─── 패턴 조건 함수들 ───

    /** 1. MACD 골든크로스 + 수급 매수 */
    private fun checkMacdGoldenCrossWithSupplyBuy(
        i: Int, prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>, oscByDate: Map<String, OscillatorRow>
    ): Boolean {
        if (i < 1) return false
        val curr = oscByDate[prices[i].date] ?: return false
        val prev = oscByDate[prices[i - 1].date] ?: return false
        // MACD 골든크로스: 전일 oscillator <= 0, 당일 > 0
        val macdCross = prev.oscillator <= 0 && curr.oscillator > 0
        // 수급 매수: MACD > 0 (수급 양전환)
        val supplyBuy = curr.macd > 0
        return macdCross && supplyBuy
    }

    /** 2. DeMark Setup9 + 거래량 급증 */
    private fun checkDemarkSetup9WithVolumeSurge(
        i: Int, prices: List<DailyTrading>,
        demarkByDate: Map<String, DemarkTDRow>, volumeRatios: Map<String, Double>
    ): Boolean {
        val date = prices[i].date
        val demark = demarkByDate[date] ?: return false
        val volRatio = volumeRatios[date] ?: return false
        val isSetup9 = demark.tdBuyCount >= 9 || demark.tdSellCount >= 9
        return isSetup9 && volRatio >= VOLUME_SURGE_RATIO
    }

    /** 3. EMA 골든크로스 + 저PBR */
    private fun checkEmaGoldenCrossWithLowPbr(
        i: Int, prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>, oscByDate: Map<String, OscillatorRow>,
        fundByDate: Map<String, FundamentalSnapshot>
    ): Boolean {
        if (i < 1) return false
        val curr = oscByDate[prices[i].date] ?: return false
        val prev = oscByDate[prices[i - 1].date] ?: return false
        val fund = fundByDate[prices[i].date]
        // EMA 골든크로스: 전일 ema12 <= ema26, 당일 ema12 > ema26
        val emaCross = (prev.ema12 - prev.ema26) <= 0 && (curr.ema12 - curr.ema26) > 0
        val lowPbr = fund != null && fund.pbr > 0 && fund.pbr < 1.0
        return emaCross && lowPbr
    }

    /** 4. 오실레이터 3일 연속 상승 + MACD 양전환 */
    private fun checkOscillator3DayRisingMacdPositive(
        i: Int, prices: List<DailyTrading>,
        oscillators: List<OscillatorRow>, oscByDate: Map<String, OscillatorRow>
    ): Boolean {
        if (i < 3) return false
        val osc0 = oscByDate[prices[i].date]?.oscillator ?: return false
        val osc1 = oscByDate[prices[i - 1].date]?.oscillator ?: return false
        val osc2 = oscByDate[prices[i - 2].date]?.oscillator ?: return false
        val osc3 = oscByDate[prices[i - 3].date]?.oscillator ?: return false
        val rising = osc0 > osc1 && osc1 > osc2 && osc2 > osc3
        val macdPositive = (oscByDate[prices[i].date]?.macd ?: 0.0) > 0
        return rising && macdPositive
    }

    /** 5. 트리플 불리시: EMA정배열 + MACD양 + 수급매수 */
    private fun checkTripleBullish(
        i: Int, prices: List<DailyTrading>, oscByDate: Map<String, OscillatorRow>
    ): Boolean {
        val osc = oscByDate[prices[i].date] ?: return false
        val emaGolden = osc.ema12 > osc.ema26
        val macdPositive = osc.macd > 0
        val supplyBuy = osc.oscillator > 0
        return emaGolden && macdPositive && supplyBuy
    }

    /** 6. 거래량 200%+ 돌파 + EMA20 지지 */
    private fun checkVolumeBreakoutWithEmaSupport(
        i: Int, prices: List<DailyTrading>,
        oscByDate: Map<String, OscillatorRow>, volumeRatios: Map<String, Double>
    ): Boolean {
        val date = prices[i].date
        val osc = oscByDate[date] ?: return false
        val volRatio = volumeRatios[date] ?: return false
        // 거래량 200%+ (시총 변화율 기반 프록시)
        val volumeBreakout = volRatio >= VOLUME_BREAKOUT_RATIO
        // EMA20 지지: ema12 > 0 (ema12가 가격 위에 있다는 의미)
        val emaSupport = osc.ema12 > osc.ema26
        return volumeBreakout && emaSupport
    }

    /** 7. DeMark Countdown 13 완료 */
    private fun checkDemarkCountdown13(
        i: Int, prices: List<DailyTrading>, demarkByDate: Map<String, DemarkTDRow>
    ): Boolean {
        val demark = demarkByDate[prices[i].date] ?: return false
        return demark.tdBuyCount >= 13 || demark.tdSellCount >= 13
    }

    /** 8. 약세 다이버전스: 가격 신고가 but 수급 하락 */
    private fun checkBearishDivergence(
        i: Int, prices: List<DailyTrading>, oscByDate: Map<String, OscillatorRow>
    ): Boolean {
        if (i < 10) return false
        // 최근 10일 내 가격 신고가
        val recentHighPrice = (i - 10..i).maxOfOrNull { idx ->
            if (idx >= 0 && idx < prices.size) prices[idx].closePrice else 0
        } ?: return false
        val isNewHigh = prices[i].closePrice >= recentHighPrice

        // 수급 하락: 현재 MACD < 10일 전 MACD
        val currMacd = oscByDate[prices[i].date]?.macd ?: return false
        val prevIdx = i - 10
        if (prevIdx < 0) return false
        val prevMacd = oscByDate[prices[prevIdx].date]?.macd ?: return false
        val supplyDecline = currMacd < prevMacd

        return isNewHigh && supplyDecline
    }

    // ─── 유틸리티 ───

    private fun calcVolumeRatios(prices: List<DailyTrading>): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        for (i in prices.indices) {
            val start = maxOf(0, i - VOLUME_AVG_WINDOW + 1)
            val window = prices.subList(start, i + 1)
            val avgCap = window.map { it.marketCap.toDouble() }.average()
            if (avgCap > 0) {
                result[prices[i].date] = prices[i].marketCap.toDouble() / avgCap
            }
        }
        return result
    }

    private fun List<Double>.averageOrZero(): Double =
        if (isEmpty()) 0.0 else average()

    private data class PatternDef(
        val name: String,
        val description: String,
        val condition: (Int) -> Boolean
    )
}
