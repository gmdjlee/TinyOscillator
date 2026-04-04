package com.tinyoscillator.presentation.chart.ext

import android.graphics.Color
import com.github.mikephil.charting.data.CandleDataSet
import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.core.testing.fixture.SyntheticData
import com.tinyoscillator.domain.model.OhlcvPoint
import org.junit.Assert.assertEquals
import org.junit.Test

@FastTest
class CandleDataExtTest {

    @Test
    fun `toCandleData produces correct entry count`() {
        val candles = SyntheticData.candles(30)
        val data = candles.toCandleData()
        assertEquals(30, data.getDataSetByIndex(0).entryCount)
    }

    @Test
    fun `toCandleData x values match index`() {
        val candles = SyntheticData.candles(5)
        val set = candles.toCandleData().getDataSetByIndex(0)
        repeat(5) { i -> assertEquals(i.toFloat(), set.getEntryForIndex(i).x) }
    }

    @Test
    fun `toVolumeBarData entry count matches candles`() {
        val candles = SyntheticData.candles(20)
        assertEquals(20, candles.toVolumeBarData().getDataSetByIndex(0).entryCount)
    }

    @Test
    fun `increasing candle has red color`() {
        val bullCandle = listOf(OhlcvPoint(0, 100f, 110f, 98f, 108f, 1_000, ""))
        val set = bullCandle.toCandleData().getDataSetByIndex(0) as CandleDataSet
        assertEquals(Color.parseColor("#D85A30"), set.increasingColor)
    }

    @Test
    fun `decreasing candle has blue color`() {
        val bearCandle = listOf(OhlcvPoint(0, 108f, 110f, 98f, 100f, 1_000, ""))
        val set = bearCandle.toCandleData().getDataSetByIndex(0) as CandleDataSet
        assertEquals(Color.parseColor("#378ADD"), set.decreasingColor)
    }

    @Test
    fun `volume bar width is 0_7`() {
        val candles = SyntheticData.candles(10)
        assertEquals(0.7f, candles.toVolumeBarData().barWidth)
    }
}
