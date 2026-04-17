package com.tinyoscillator.presentation.chart.ext

import com.tinyoscillator.core.testing.annotations.FastTest
import com.tinyoscillator.domain.model.OhlcvPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(FastTest::class)
class WeeklyOhlcvResamplerTest {

    /** ISO week 기준 2024년 1월 1일(월)~7일(일)은 동일한 주(W1) */
    @Test
    fun `single ISO week aggregates into one weekly candle`() {
        val daily = listOf(
            OhlcvPoint(0, open = 100f, high = 110f, low = 95f,  close = 105f, volume = 1_000, date = "20240101"), // 월
            OhlcvPoint(1, open = 105f, high = 120f, low = 100f, close = 115f, volume = 2_000, date = "20240102"),
            OhlcvPoint(2, open = 115f, high = 118f, low = 108f, close = 112f, volume = 1_500, date = "20240103"),
            OhlcvPoint(3, open = 112f, high = 116f, low =  90f, close =  92f, volume = 3_000, date = "20240104"),
            OhlcvPoint(4, open =  92f, high = 105f, low =  88f, close = 100f, volume = 2_500, date = "20240105"), // 금
        )

        val weekly = daily.resampleToWeekly()

        assertEquals(1, weekly.size)
        val w = weekly[0]
        assertEquals(0, w.index)
        assertEquals(100f, w.open, 0.001f)   // 월요일 open
        assertEquals(120f, w.high, 0.001f)   // max high (화요일)
        assertEquals(88f, w.low, 0.001f)     // min low (금요일)
        assertEquals(100f, w.close, 0.001f)  // 금요일 close
        assertEquals(10_000L, w.volume)      // 합산
        assertEquals("20240105", w.date)     // 주 마지막 거래일
    }

    @Test
    fun `multiple weeks produce sequential reindexed points`() {
        val daily = listOf(
            // W1 (2024-01-01 ~ 2024-01-07)
            OhlcvPoint(0, 100f, 110f, 95f, 105f, 1_000, "20240102"),
            OhlcvPoint(1, 105f, 115f, 100f, 110f, 1_200, "20240104"),
            // W2 (2024-01-08 ~ 2024-01-14)
            OhlcvPoint(2, 110f, 125f, 108f, 120f, 2_000, "20240108"),
            OhlcvPoint(3, 120f, 130f, 118f, 125f, 1_800, "20240112"),
            // W3 (2024-01-15 ~ 2024-01-21)
            OhlcvPoint(4, 125f, 128f, 115f, 118f, 1_500, "20240115"),
        )

        val weekly = daily.resampleToWeekly()

        assertEquals(3, weekly.size)
        assertEquals(listOf(0, 1, 2), weekly.map { it.index })
        assertEquals("20240104", weekly[0].date)
        assertEquals("20240112", weekly[1].date)
        assertEquals("20240115", weekly[2].date)
        assertEquals(110f, weekly[0].close, 0.001f)
        assertEquals(125f, weekly[1].close, 0.001f)
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(emptyList<OhlcvPoint>().resampleToWeekly().isEmpty())
    }

    @Test
    fun `unsorted input still groups correctly by ISO week`() {
        val daily = listOf(
            OhlcvPoint(0, 105f, 115f, 100f, 110f, 1_200, "20240104"),
            OhlcvPoint(1, 100f, 110f, 95f,  105f, 1_000, "20240102"),
        )

        val weekly = daily.resampleToWeekly()

        assertEquals(1, weekly.size)
        assertEquals(100f, weekly[0].open, 0.001f)    // 01-02가 주 첫 거래일
        assertEquals(110f, weekly[0].close, 0.001f)   // 01-04가 주 마지막 거래일
        assertEquals("20240104", weekly[0].date)
    }

    @Test
    fun `unparseable date entries are dropped`() {
        val daily = listOf(
            OhlcvPoint(0, 100f, 110f, 95f, 105f, 1_000, "INVALID"),
            OhlcvPoint(1, 105f, 115f, 100f, 110f, 1_200, "20240104"),
        )

        val weekly = daily.resampleToWeekly()

        assertEquals(1, weekly.size)
        assertEquals(1_200L, weekly[0].volume)  // INVALID 제외됨
    }

    @Test
    fun `toDateLabelsFromOhlcv formats as MM slash dd`() {
        val points = listOf(
            OhlcvPoint(0, 0f, 0f, 0f, 0f, 0, "20240105"),
            OhlcvPoint(1, 0f, 0f, 0f, 0f, 0, "20240112"),
        )
        val labels = points.toDateLabelsFromOhlcv()
        assertEquals("01/05", labels[0])
        assertEquals("01/12", labels[1])
    }
}
