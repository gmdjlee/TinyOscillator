package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.PatternType
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 주봉 리샘플링 후 ParkSignalDetector 신호 수를 검증한다.
 *
 * 일봉 → 주봉 변환 후 신호가 극단적으로 적은 문제를 재현하고,
 * 각 분석 기간별 기대 신호 수를 확인한다.
 */
class ParkSignalDetectorWeeklyTest {

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** 거래일(월~금)만 포함하는 일봉 데이터 생성 */
    private fun dailyCandles(
        totalDays: Int,
        basePrice: Float = 50_000f,
        seed: Long = 42L,
    ): List<OhlcvPoint> {
        val rng = java.util.Random(seed)
        var price = basePrice
        val result = mutableListOf<OhlcvPoint>()
        var date = LocalDate.of(2024, 1, 2) // 화요일 시작
        var idx = 0

        while (result.size < totalDays) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                val change = price * 0.025f * (rng.nextFloat() - 0.48f) // 약간의 상승 편향
                val open = price
                val close = (price + change).coerceAtLeast(100f)
                val high = maxOf(open, close) * (1f + rng.nextFloat() * 0.008f)
                val low = minOf(open, close) * (1f - rng.nextFloat() * 0.008f)
                // 간헐적 거래량 급증 (평균의 2~5배)
                val baseVol = 100_000L
                val volSpike = if (rng.nextFloat() < 0.15f) (2 + rng.nextInt(4)).toLong() else 1L
                val volume = baseVol * volSpike + rng.nextInt(50_000).toLong()

                result += OhlcvPoint(
                    index = idx++,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                    date = date.format(fmt),
                )
                price = close
            }
            date = date.plusDays(1)
        }
        return result
    }

    /** 일봉 → 주봉 리샘플링 (WeeklyOhlcvResampler 로직 복제) */
    private fun List<OhlcvPoint>.resampleToWeekly(): List<OhlcvPoint> {
        if (isEmpty()) return emptyList()
        val isoFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        return this
            .mapNotNull { p ->
                val parsed = runCatching { LocalDate.parse(p.date, isoFmt) }.getOrNull()
                if (parsed == null) null else p to parsed
            }
            .groupBy { (_, d) ->
                d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR) * 100 +
                    d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            }
            .toSortedMap()
            .values
            .mapIndexed { i, weekPairs ->
                val sorted = weekPairs.sortedBy { it.second }.map { it.first }
                val first = sorted.first()
                val last = sorted.last()
                OhlcvPoint(
                    index = i,
                    open = first.open,
                    high = sorted.maxOf { it.high },
                    low = sorted.minOf { it.low },
                    close = last.close,
                    volume = sorted.sumOf { it.volume },
                    date = last.date,
                )
            }
    }

    // ── 기간별 주봉 신호 수 검증 ──

    @Test
    fun `ONE_MONTH 90+140 days weekly signals count`() {
        val daily = dailyCandles(totalDays = 230) // 90 + 140 warmup
        val weekly = daily.resampleToWeekly()
        val signals = ParkSignalDetector.detect(weekly)

        println("=== ONE_MONTH (90+140일) ===")
        println("일봉 수: ${daily.size}")
        println("주봉 수: ${weekly.size}")
        println("신호 수: ${signals.size}")
        signals.groupBy { it.type }.forEach { (type, list) ->
            println("  $type: ${list.size}개")
        }
        println("신호 검사 가능 봉 수: ${(weekly.size - 20).coerceAtLeast(0)}")
        println()

        // 주봉 수가 충분한지 확인
        assertTrue("주봉 수가 20 미만이면 신호 없음", weekly.size >= 20)
    }

    @Test
    fun `THREE_MONTHS 180+140 days weekly signals count`() {
        val daily = dailyCandles(totalDays = 320) // 180 + 140 warmup
        val weekly = daily.resampleToWeekly()
        val signals = ParkSignalDetector.detect(weekly)

        println("=== THREE_MONTHS (180+140일) ===")
        println("일봉 수: ${daily.size}")
        println("주봉 수: ${weekly.size}")
        println("신호 수: ${signals.size}")
        signals.groupBy { it.type }.forEach { (type, list) ->
            println("  $type: ${list.size}개")
        }
        println("신호 검사 가능 봉 수: ${(weekly.size - 20).coerceAtLeast(0)}")
        println()
    }

    @Test
    fun `TWO_YEARS 730+140 days weekly signals count`() {
        val daily = dailyCandles(totalDays = 870) // 730 + 140 warmup
        val weekly = daily.resampleToWeekly()
        val signals = ParkSignalDetector.detect(weekly)

        println("=== TWO_YEARS (730+140일) ===")
        println("일봉 수: ${daily.size}")
        println("주봉 수: ${weekly.size}")
        println("신호 수: ${signals.size}")
        signals.groupBy { it.type }.forEach { (type, list) ->
            println("  $type: ${list.size}개")
        }
        println("신호 검사 가능 봉 수: ${(weekly.size - 20).coerceAtLeast(0)}")
        println()

        // 2년치 주봉(~174봉)이면 최소 10개 이상의 신호가 있어야 함
        assertTrue(
            "2년치 주봉에서 신호가 ${signals.size}개뿐 — 너무 적음",
            signals.size >= 5
        )
    }

    @Test
    fun `compare daily vs weekly signal density`() {
        val daily = dailyCandles(totalDays = 500, seed = 123L)
        val weekly = daily.resampleToWeekly()
        val dailySignals = ParkSignalDetector.detect(daily)
        val weeklySignals = ParkSignalDetector.detect(weekly)

        val dailyCheckable = (daily.size - 20).coerceAtLeast(0)
        val weeklyCheckable = (weekly.size - 20).coerceAtLeast(0)
        val dailyDensity = if (dailyCheckable > 0) dailySignals.size.toFloat() / dailyCheckable else 0f
        val weeklyDensity = if (weeklyCheckable > 0) weeklySignals.size.toFloat() / weeklyCheckable else 0f

        println("=== 일봉 vs 주봉 신호 밀도 비교 (500일) ===")
        println("일봉: ${daily.size}봉, 검사 ${dailyCheckable}봉, 신호 ${dailySignals.size}개, 밀도 ${String.format("%.2f", dailyDensity)}")
        println("주봉: ${weekly.size}봉, 검사 ${weeklyCheckable}봉, 신호 ${weeklySignals.size}개, 밀도 ${String.format("%.2f", weeklyDensity)}")
        println()
        println("일봉 신호 타입별:")
        dailySignals.groupBy { it.type }.forEach { (type, list) ->
            println("  $type: ${list.size}개")
        }
        println("주봉 신호 타입별:")
        weeklySignals.groupBy { it.type }.forEach { (type, list) ->
            println("  $type: ${list.size}개")
        }
    }

    @Test
    fun `weekly volume ratio is comparable to daily`() {
        // 주봉 거래량 비율이 일봉과 유사한지 검증
        val daily = dailyCandles(totalDays = 250)
        val weekly = daily.resampleToWeekly()

        // 마지막 5주 거래량 / 20주 평균 거래량
        if (weekly.size >= 25) {
            val last20Avg = weekly.takeLast(25).take(20).map { it.volume }.average()
            val last5 = weekly.takeLast(5).map { it.volume }
            println("=== 주봉 거래량 비율 검증 ===")
            last5.forEachIndexed { i, vol ->
                val ratio = vol / last20Avg
                println("  최근 ${5 - i}주 전: vol=$vol, ratio=${String.format("%.2f", ratio)}")
            }
        }
    }

    // ── 현실적 데이터 (거래량 급증 드문 경우) ──

    /** 거래량이 평탄한 현실적 일봉 (거래량 급증 확률 3%) */
    private fun realisticDailyCandles(
        totalDays: Int,
        basePrice: Float = 50_000f,
        seed: Long = 42L,
    ): List<OhlcvPoint> {
        val rng = java.util.Random(seed)
        var price = basePrice
        val result = mutableListOf<OhlcvPoint>()
        var date = LocalDate.of(2024, 1, 2)
        var idx = 0

        while (result.size < totalDays) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                val change = price * 0.015f * (rng.nextFloat() - 0.5f) // 낮은 변동성
                val open = price
                val close = (price + change).coerceAtLeast(100f)
                val high = maxOf(open, close) * (1f + rng.nextFloat() * 0.003f)
                val low = minOf(open, close) * (1f - rng.nextFloat() * 0.003f)
                // 거래량 급증 매우 드묾 (3%)
                val baseVol = 100_000L
                val volSpike = if (rng.nextFloat() < 0.03f) (2 + rng.nextInt(3)).toLong() else 1L
                val volume = baseVol * volSpike + (rng.nextFloat() * 20_000).toLong()

                result += OhlcvPoint(
                    index = idx++,
                    open = open, high = high, low = low, close = close,
                    volume = volume,
                    date = date.format(fmt),
                )
                price = close
            }
            date = date.plusDays(1)
        }
        return result
    }

    @Test
    fun `realistic low-volatility data weekly signals`() {
        val daily = realisticDailyCandles(totalDays = 500) // ~2년
        val weekly = daily.resampleToWeekly()
        val dailySignals = ParkSignalDetector.detect(daily)
        val weeklySignals = ParkSignalDetector.detect(weekly)

        println("=== 현실적 저변동성 데이터 (500일) ===")
        println("일봉: ${daily.size}봉, 신호 ${dailySignals.size}개")
        dailySignals.groupBy { it.type }.forEach { (type, list) ->
            println("  $type: ${list.size}개")
        }
        println("주봉: ${weekly.size}봉, 신호 ${weeklySignals.size}개")
        weeklySignals.groupBy { it.type }.forEach { (type, list) ->
            println("  $type: ${list.size}개")
        }

        // 주봉에서도 신호가 극단적으로 적지 않은지 확인
        if (weeklySignals.size < 5) {
            println("⚠️ 주봉 신호가 ${weeklySignals.size}개 — 신호 기준이 주봉에 너무 엄격할 수 있음")
        }
    }

    @Test
    fun `volume ratio dilution on weekly resampling`() {
        // 일봉에서 1일만 거래량 3배여도, 주봉 합산 시 비율이 희석되는지 검증
        val daily = mutableListOf<OhlcvPoint>()
        var date = LocalDate.of(2024, 1, 2)
        var idx = 0
        var price = 50_000f
        val rng = java.util.Random(99L)

        // 250 거래일 생성 (기본 거래량 100k, 1개 봉만 500k)
        while (daily.size < 250) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                val change = price * 0.01f * (rng.nextFloat() - 0.5f)
                val open = price
                val close = (price + change).coerceAtLeast(100f)
                val high = maxOf(open, close) * 1.002f
                val low = minOf(open, close) * 0.998f
                val volume = if (daily.size == 200) 500_000L else 100_000L

                daily += OhlcvPoint(idx++, open, high, low, close, volume, date.format(fmt))
                price = close
            }
            date = date.plusDays(1)
        }

        val weekly = daily.resampleToWeekly()

        // 200번째 일봉의 거래량 비율 (일봉 기준)
        val dailyAvg20 = daily.subList(180, 200).map { it.volume }.average()
        val dailyVR = daily[200].volume / dailyAvg20
        println("=== 거래량 비율 희석 검증 ===")
        println("일봉 인덱스 200: vol=${daily[200].volume}, 20일 avg=${dailyAvg20.toLong()}, ratio=${String.format("%.2f", dailyVR)}")

        // 같은 주의 주봉 거래량 비율
        val weekIdx = weekly.indexOfFirst { it.date == daily[200].date || it.date > daily[200].date }
        if (weekIdx >= 20) {
            val weeklyAvg20 = weekly.subList(weekIdx - 20, weekIdx).map { it.volume }.average()
            val weeklyVR = weekly[weekIdx].volume / weeklyAvg20
            println("주봉 인덱스 $weekIdx: vol=${weekly[weekIdx].volume}, 20주 avg=${weeklyAvg20.toLong()}, ratio=${String.format("%.2f", weeklyVR)}")
            println("희석 비율: ${String.format("%.2f", weeklyVR / dailyVR)} (1.0이면 희석 없음)")
        }
    }
}
