package com.tinyoscillator.presentation.common

import com.tinyoscillator.domain.model.HeatmapData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HeatmapDataTest {

    private fun makeData(tickers: List<String>, days: Int): HeatmapData {
        val now = System.currentTimeMillis()
        val dates = (days - 1 downTo 0).map { now - it * 86_400_000L }
        val scores = tickers.associateWith { _ ->
            dates.mapIndexed { i, _ -> 0.5f + i * 0.01f }
        }
        return HeatmapData(tickers, emptyMap(), dates,
            dates.map { "날짜" }, scores)
    }

    @Test
    fun `scoreAt returns correct value for valid indices`() {
        val data = makeData(listOf("005930"), 5)
        assertEquals(0.5f, data.scoreAt("005930", 0), 0.001f)
        assertEquals(0.54f, data.scoreAt("005930", 4), 0.001f)
    }

    @Test
    fun `scoreAt returns 0_5 for unknown ticker`() {
        val data = makeData(listOf("005930"), 5)
        assertEquals(0.5f, data.scoreAt("999999", 0))
    }

    @Test
    fun `scoreAt returns 0_5 for out of bounds index`() {
        val data = makeData(listOf("005930"), 5)
        assertEquals(0.5f, data.scoreAt("005930", 99))
    }

    @Test
    fun `scores list length matches dates list`() {
        val days = 20
        val data = makeData(listOf("005930", "035720"), days)
        data.scores.values.forEach { scoreList ->
            assertEquals(days, scoreList.size)
        }
    }

    @Test
    fun `empty tickers produce valid empty data`() {
        val data = HeatmapData(
            tickers = emptyList(),
            tickerNames = emptyMap(),
            dates = emptyList(),
            dateLabels = emptyList(),
            scores = emptyMap(),
        )
        assertEquals(0.5f, data.scoreAt("any", 0))
        assertEquals(0, data.tickers.size)
    }

    @Test
    fun `tickerNames maps correctly`() {
        val data = HeatmapData(
            tickers = listOf("005930"),
            tickerNames = mapOf("005930" to "삼성전자"),
            dates = listOf(1L),
            dateLabels = listOf("04.05"),
            scores = mapOf("005930" to listOf(0.8f)),
        )
        assertEquals("삼성전자", data.tickerNames["005930"])
    }
}
