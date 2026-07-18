package com.tinyoscillator.presentation.chart.ext

import androidx.core.graphics.ColorUtils
import com.github.mikephil.charting.data.CandleDataSet
import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.core.testing.fixture.SyntheticData
import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.presentation.chart.ChartTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(FastTest::class)
class CandleDataExtTest {

    // 골든 팔레트 — 프로덕션 토큰(양봉 적/음봉 청/심지 무채색)의 대표 ARGB 값.
    // 리터럴로 고정해 android.graphics 목킹과 무관하게 색 배선을 검증한다.
    private val theme = ChartTheme(
        neutralLine = 0xFF1976D2.toInt(),
        emphasisLine = 0xFF6ECBA8.toInt(),
        positive = 0xFFD85A30.toInt(),
        negative = 0xFF378ADD.toInt(),
        grid = 0xFF444444.toInt(),
        axisText = 0xFFA8A4A0.toInt(),
        holeFill = 0xFF0B0E14.toInt(),
        neutral = 0xFF888780.toInt(),
        isDark = true,
    )

    @Test
    fun `toCandleData produces correct entry count`() {
        val candles = SyntheticData.candles(30)
        val data = candles.toCandleData(theme)
        assertEquals(30, data.getDataSetByIndex(0).entryCount)
    }

    @Test
    fun `toCandleData x values match index`() {
        val candles = SyntheticData.candles(5)
        val set = candles.toCandleData(theme).getDataSetByIndex(0)
        repeat(5) { i -> assertEquals(i.toFloat(), set.getEntryForIndex(i).x) }
    }

    @Test
    fun `toVolumeBarData entry count matches candles`() {
        val candles = SyntheticData.candles(20)
        assertEquals(20, candles.toVolumeBarData(theme).getDataSetByIndex(0).entryCount)
    }

    @Test
    fun `increasing candle uses theme positive color`() {
        val bullCandle = listOf(OhlcvPoint(0, 100f, 110f, 98f, 108f, 1_000, ""))
        val set = bullCandle.toCandleData(theme).getDataSetByIndex(0) as CandleDataSet
        assertEquals(theme.positive, set.increasingColor)
    }

    @Test
    fun `decreasing candle uses theme negative color`() {
        val bearCandle = listOf(OhlcvPoint(0, 108f, 110f, 98f, 100f, 1_000, ""))
        val set = bearCandle.toCandleData(theme).getDataSetByIndex(0) as CandleDataSet
        assertEquals(theme.negative, set.decreasingColor)
    }

    @Test
    fun `candle shadow uses theme neutral color`() {
        val set = SyntheticData.candles(3).toCandleData(theme).getDataSetByIndex(0) as CandleDataSet
        assertEquals(theme.neutral, set.shadowColor)
    }

    @Test
    fun `increasing volume bar applies 0x55 alpha over positive`() {
        val bull = listOf(OhlcvPoint(0, 100f, 110f, 98f, 108f, 1_000, ""))
        val set = bull.toVolumeBarData(theme).getDataSetByIndex(0)
        assertEquals(ColorUtils.setAlphaComponent(theme.positive, 0x55), set.colors[0])
    }

    @Test
    fun `decreasing volume bar applies 0x55 alpha over negative`() {
        val bear = listOf(OhlcvPoint(0, 108f, 110f, 98f, 100f, 1_000, ""))
        val set = bear.toVolumeBarData(theme).getDataSetByIndex(0)
        assertEquals(ColorUtils.setAlphaComponent(theme.negative, 0x55), set.colors[0])
    }

    @Test
    fun `volume bar width is 0_7`() {
        val candles = SyntheticData.candles(10)
        assertEquals(0.7f, candles.toVolumeBarData(theme).barWidth)
    }
}
