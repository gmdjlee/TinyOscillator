package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.PatternAnalysis
import com.tinyoscillator.domain.model.SignalConflict
import com.tinyoscillator.domain.model.SignalContribution
import com.tinyoscillator.domain.model.SignalScoringResult
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 가중 신호 앙상블 스코어링 엔진
 *
 * 각 지표 신호에 과거 승률 기반 가중치를 부여하여 0~100 종합 점수 산출.
 * Score = Σ(wᵢ × signalᵢ × directionᵢ) / Σ(wᵢ) × 100
 *
 * - wᵢ = 해당 지표의 과거 20일 승률 (PatternScanEngine 결과에서 가져옴)
 * - signalᵢ = 1(활성) or 0(비활성)
 * - directionᵢ = +1(매수) or -1(매도)
 */
@Singleton
class SignalScoringEngine @Inject constructor() {

    companion object {
        private const val VOLUME_AVG_WINDOW = 20
        private const val VOLUME_SURGE_RATIO = 1.5
        // 기본 가중치 (PatternAnalysis 없을 때 fallback)
        private const val DEFAULT_WEIGHT = 0.5
    }

    /**
     * 신호 스코어링 실행
     *
     * @param oscillators 오실레이터 데이터
     * @param demarkRows DeMark TD 데이터
     * @param prices 일별 거래 데이터
     * @param fundamentals 펀더멘털 데이터
     * @param patternAnalysis 패턴 분석 결과 (승률 기반 가중치 소스, nullable)
     */
    suspend fun analyze(
        oscillators: List<OscillatorRow>,
        demarkRows: List<DemarkTDRow>,
        prices: List<DailyTrading>,
        fundamentals: List<FundamentalSnapshot>?,
        patternAnalysis: PatternAnalysis? = null
    ): SignalScoringResult {
        if (oscillators.isEmpty()) {
            return SignalScoringResult(50, emptyList(), emptyList(), "MIXED")
        }

        val currentOsc = oscillators.last()
        val currentDemark = demarkRows.lastOrNull()
        val currentFund = fundamentals?.lastOrNull()
        val volumeRatio = calcCurrentVolumeRatio(prices)

        // 패턴 승률에서 가중치 추출
        val patternWinRates = patternAnalysis?.allPatterns
            ?.associate { it.patternName to it.winRate20d } ?: emptyMap()

        // 각 신호 평가
        val signals = mutableListOf<SignalEntry>()

        // 1. MACD 신호
        val macdDirection = if (currentOsc.oscillator > 0) +1 else if (currentOsc.oscillator < 0) -1 else 0
        signals.add(SignalEntry(
            name = "MACD",
            signal = if (macdDirection != 0) 1 else 0,
            direction = macdDirection,
            weight = patternWinRates["MACD_GOLDEN_CROSS_WITH_SUPPLY_BUY"] ?: DEFAULT_WEIGHT
        ))

        // 2. 수급 오실레이터 신호
        val supplyDirection = if (currentOsc.macd > 0) +1 else if (currentOsc.macd < 0) -1 else 0
        signals.add(SignalEntry(
            name = "수급오실레이터",
            signal = if (supplyDirection != 0) 1 else 0,
            direction = supplyDirection,
            weight = patternWinRates["TRIPLE_BULLISH"] ?: DEFAULT_WEIGHT
        ))

        // 3. EMA 배열 신호
        val emaDirection = if (currentOsc.ema12 > currentOsc.ema26) +1
            else if (currentOsc.ema12 < currentOsc.ema26) -1 else 0
        signals.add(SignalEntry(
            name = "EMA배열",
            signal = if (emaDirection != 0) 1 else 0,
            direction = emaDirection,
            weight = patternWinRates["EMA_GOLDEN_CROSS_WITH_LOW_PBR"] ?: DEFAULT_WEIGHT
        ))

        // 4. DeMark 신호
        if (currentDemark != null) {
            val demarkDirection = when {
                currentDemark.tdBuyCount >= 7 -> +1   // 매수 피로 = 반전 매수 근접
                currentDemark.tdSellCount >= 7 -> -1  // 매도 피로 = 반전 매도 근접
                else -> 0
            }
            signals.add(SignalEntry(
                name = "DeMark",
                signal = if (abs(demarkDirection) > 0) 1 else 0,
                direction = demarkDirection,
                weight = patternWinRates["DEMARK_SETUP9_WITH_VOLUME_SURGE"] ?: DEFAULT_WEIGHT
            ))
        }

        // 5. 거래량 신호 (가중치 강화)
        if (volumeRatio != null && volumeRatio >= VOLUME_SURGE_RATIO) {
            signals.add(SignalEntry(
                name = "거래량급증",
                signal = 1,
                direction = if (currentOsc.oscillator > 0) +1 else -1, // 추세 방향으로 강화
                weight = patternWinRates["VOLUME_BREAKOUT_WITH_EMA_SUPPORT"] ?: DEFAULT_WEIGHT
            ))
        }

        // 6. PBR 신호
        if (currentFund != null && currentFund.pbr > 0) {
            val pbrDirection = if (currentFund.pbr < 1.0) +1 else 0
            signals.add(SignalEntry(
                name = "PBR저평가",
                signal = if (pbrDirection > 0) 1 else 0,
                direction = pbrDirection,
                weight = patternWinRates["EMA_GOLDEN_CROSS_WITH_LOW_PBR"] ?: DEFAULT_WEIGHT
            ))
        }

        // 점수 계산
        val activeSignals = signals.filter { it.signal > 0 }
        val totalWeight = signals.sumOf { it.weight }

        val rawScore = if (totalWeight > 0) {
            val weightedSum = signals.sumOf { it.weight * it.signal * it.direction }
            // -1~+1 범위를 0~100으로 변환
            ((weightedSum / totalWeight + 1.0) / 2.0 * 100).roundToInt().coerceIn(0, 100)
        } else 50

        // 기여도 계산
        val contributions = signals.map { entry ->
            val contribution = if (totalWeight > 0)
                (entry.weight * entry.signal * abs(entry.direction)) / totalWeight * 100 else 0.0
            SignalContribution(
                name = entry.name,
                weight = entry.weight,
                signal = entry.signal,
                direction = entry.direction,
                contributionPercent = contribution
            )
        }

        // 충돌 신호 탐지
        val conflicts = detectConflicts(signals)

        // 지배 방향 판정
        val bullishCount = activeSignals.count { it.direction > 0 }
        val bearishCount = activeSignals.count { it.direction < 0 }
        val dominantDirection = when {
            bullishCount > bearishCount * 2 -> "BULLISH"
            bearishCount > bullishCount * 2 -> "BEARISH"
            bullishCount > bearishCount -> "MILDLY_BULLISH"
            bearishCount > bullishCount -> "MILDLY_BEARISH"
            else -> "MIXED"
        }

        return SignalScoringResult(
            totalScore = rawScore,
            contributions = contributions,
            conflictingSignals = conflicts,
            dominantDirection = dominantDirection
        )
    }

    private fun detectConflicts(signals: List<SignalEntry>): List<SignalConflict> {
        val conflicts = mutableListOf<SignalConflict>()
        val active = signals.filter { it.signal > 0 && it.direction != 0 }

        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                if (active[i].direction != active[j].direction) {
                    val dir1 = if (active[i].direction > 0) "매수" else "매도"
                    val dir2 = if (active[j].direction > 0) "매수" else "매도"
                    conflicts.add(SignalConflict(
                        signal1 = active[i].name,
                        signal2 = active[j].name,
                        direction1 = dir1,
                        direction2 = dir2,
                        description = "${active[i].name}($dir1)과 ${active[j].name}($dir2) 충돌"
                    ))
                }
            }
        }
        return conflicts
    }

    private fun calcCurrentVolumeRatio(prices: List<DailyTrading>): Double? {
        if (prices.size < VOLUME_AVG_WINDOW) return null
        val recent = prices.takeLast(VOLUME_AVG_WINDOW)
        val avgCap = recent.map { it.marketCap.toDouble() }.average()
        return if (avgCap > 0) prices.last().marketCap.toDouble() / avgCap else null
    }

    private data class SignalEntry(
        val name: String,
        val signal: Int,      // 0 or 1
        val direction: Int,   // +1 매수, -1 매도, 0 중립
        val weight: Double
    )
}
