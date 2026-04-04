package com.tinyoscillator.domain.usecase

import com.tinyoscillator.data.engine.VectorizedIndicators
import com.tinyoscillator.domain.model.*
import com.tinyoscillator.domain.model.OscillatorConfig.Companion.MARKET_CAP_DIVISOR
import timber.log.Timber

/**
 * 수급 오실레이터 계산 UseCase
 *
 * 엑셀 전체 데이터 흐름을 Kotlin으로 1:1 매핑:
 *
 * Step 1: 원천 데이터 (시가총액, 외국인/기관 순매수)
 * Step 2: 5일 누적 순매수 계산 (rolling window = 5, 개장일 기준)
 * Step 3: 수급 비율 = (외국인5일합 + 기관5일합) / 시가총액
 * Step 4: EMA 12일, 26일 계산
 * Step 5: MACD = EMA12 - EMA26
 * Step 6: 시그널 = EMA(MACD, 9일)
 * Step 7: 오실레이터 = MACD - 시그널
 */
class CalcOscillatorUseCase(
    private val config: OscillatorConfig = OscillatorConfig()
) {
    /**
     * 전체 오실레이터 계산 파이프라인
     * (EMA warmup 없이 표시 기간만으로 계산 — 디버깅용)
     */
    fun execute(dailyData: List<DailyTrading>, warmupCount: Int = 0): List<OscillatorRow> {
        require(dailyData.isNotEmpty()) { "일별 데이터가 비어있습니다" }
        require(warmupCount in 0..< dailyData.size) { "warmupCount가 데이터 범위를 벗어났습니다: warmupCount=$warmupCount, dataSize=${dailyData.size}" }

        Timber.d("━━━ 오실레이터 계산 시작 ━━━")
        Timber.d("전체 데이터: %d일, warmupCount: %d, 표시: %d일", dailyData.size, warmupCount, dailyData.size - warmupCount)

        // Step 2: 5일 누적 순매수
        val cumData = calc5DayRolling(dailyData)

        // warmup 이후 표시 데이터만 추출
        val displayData = cumData.subList(warmupCount, cumData.size)

        // Step 3: 수급 비율 (표시 기간만)
        val supplyRatios = displayData.map { (daily, f5d, i5d) ->
            if (daily.marketCap == 0L) 0.0
            else (f5d + i5d).toDouble() / daily.marketCap.toDouble()
        }

        // Step 4: EMA 12일, 26일 (표시 기간만으로 계산)
        val ema12 = calcEma(supplyRatios, config.emaFast)
        val ema26 = calcEma(supplyRatios, config.emaSlow)

        // Step 5: MACD = EMA12 - EMA26
        val macd = ema12.zip(ema26) { e12, e26 -> e12 - e26 }

        // Step 6: 시그널 = EMA(MACD, 9일)
        val signal = calcEma(macd, config.emaSignal)

        // Step 7: 결과 생성
        val rows = displayData.indices.map { i ->
            val (daily, f5d, i5d) = displayData[i]
            OscillatorRow(
                date = daily.date,
                marketCap = daily.marketCap,
                marketCapTril = daily.marketCap / MARKET_CAP_DIVISOR,
                foreign5d = f5d,
                inst5d = i5d,
                supplyRatio = supplyRatios[i],
                ema12 = ema12[i],
                ema26 = ema26[i],
                macd = macd[i],
                signal = signal[i],
                oscillator = macd[i] - signal[i]
            )
        }

        Timber.d("━━━ 계산 결과 (마지막 5일) ━━━")
        rows.takeLast(5).forEach { row ->
            Timber.d("[${row.date}] 시총=${row.marketCap}원 (${String.format("%.2f", row.marketCapTril)}조)" +
                    " | 외5d=${row.foreign5d} 기5d=${row.inst5d}" +
                    " | 수급비율=${String.format("%.8f", row.supplyRatio)}" +
                    " | EMA12=${String.format("%.8f", row.ema12)} EMA26=${String.format("%.8f", row.ema26)}" +
                    " | MACD=${String.format("%.8f", row.macd)} Signal=${String.format("%.8f", row.signal)}" +
                    " | 오실레이터=${String.format("%.8f", row.oscillator)} (차트표시: ${String.format("%.4f", row.oscillator * 100)}%)")
        }
        Timber.d("━━━ 오실레이터 계산 완료 ━━━")

        return rows
    }

    /**
     * Step 2: 5일 누적 순매수 계산 (개장일 기준)
     */
    private fun calc5DayRolling(
        data: List<DailyTrading>
    ): List<Triple<DailyTrading, Long, Long>> {
        if (data.isEmpty()) return emptyList()
        val window = config.rollingWindow
        val result = ArrayList<Triple<DailyTrading, Long, Long>>(data.size)
        var foreignSum = 0L
        var instSum = 0L
        for (i in data.indices) {
            foreignSum += data[i].foreignNetBuy
            instSum += data[i].instNetBuy
            if (i >= window) {
                foreignSum -= data[i - window].foreignNetBuy
                instSum -= data[i - window].instNetBuy
            }
            result.add(Triple(data[i], foreignSum, instSum))
        }
        return result
    }

    /**
     * EMA (지수이동평균) 계산
     *
     * pandas.ewm(alpha=..., adjust=False) 와 동일.
     * VectorizedIndicators.emaArray를 래핑하여 DoubleArray 기반으로 최적화.
     */
    fun calcEma(values: List<Double>, period: Int): List<Double> {
        return VectorizedIndicators.emaList(values, period)
    }

    /**
     * 매매 신호 분석
     */
    fun analyzeSignals(rows: List<OscillatorRow>): List<SignalAnalysis> {
        return rows.mapIndexed { i, row ->
            val cross = if (i > 0) {
                val prevOsc = rows[i - 1].oscillator
                when {
                    prevOsc <= 0 && row.oscillator > 0 -> CrossSignal.GOLDEN_CROSS
                    prevOsc >= 0 && row.oscillator < 0 -> CrossSignal.DEAD_CROSS
                    else -> null
                }
            } else null

            val trend = when {
                row.oscillator > 0 && row.macd > 0 -> Trend.BULLISH
                row.oscillator < 0 && row.macd < 0 -> Trend.BEARISH
                else -> Trend.NEUTRAL
            }

            SignalAnalysis(
                date = row.date,
                marketCapTril = row.marketCapTril,
                oscillator = row.oscillator,
                macd = row.macd,
                signal = row.signal,
                trend = trend,
                crossSignal = cross
            )
        }
    }

}
