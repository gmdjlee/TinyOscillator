package com.tinyoscillator.domain

import com.tinyoscillator.data.repository.StockGroupRepository
import com.tinyoscillator.domain.model.DEFAULT_THEMES
import com.tinyoscillator.domain.model.GroupType
import com.tinyoscillator.domain.model.StockGroup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockGroupAggregationTest {

    @Test
    fun `average signal is mean of all tickers`() {
        val signals = listOf(0.7f, 0.8f, 0.6f)
        val avg = signals.average().toFloat()
        assertEquals(0.7f, avg, 0.001f)
    }

    @Test
    fun `empty ticker list gives neutral 0_5 average`() {
        val signals = emptyList<Float>()
        val avg = if (signals.isEmpty()) 0.5f else signals.average().toFloat()
        assertEquals(0.5f, avg)
    }

    @Test
    fun `groups sorted descending by avgSignal`() {
        val groups = listOf(
            StockGroup(1L, "A", GroupType.KRX_SECTOR, emptyList(), avgSignal = 0.65f),
            StockGroup(2L, "B", GroupType.KRX_SECTOR, emptyList(), avgSignal = 0.80f),
            StockGroup(3L, "C", GroupType.KRX_SECTOR, emptyList(), avgSignal = 0.55f),
        ).sortedByDescending { it.avgSignal }
        assertEquals(listOf("B", "A", "C"), groups.map { it.name })
    }

    @Test
    fun `DEFAULT_THEMES contains at least 5 entries`() {
        assertTrue(DEFAULT_THEMES.size >= 5)
    }

    @Test
    fun `DEFAULT_THEMES all have non-empty ticker lists`() {
        DEFAULT_THEMES.forEach { (name, tickers) ->
            assertTrue("$name has empty ticker list", tickers.isNotEmpty())
        }
    }

    @Test
    fun `user theme ticker JSON round trip`() {
        val original = listOf("005930", "000660", "373220")
        val json = Json.encodeToString(original)
        val decoded = Json.decodeFromString<List<String>>(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `parseTickerList handles valid JSON`() {
        val json = """["005930","000660"]"""
        val result = StockGroupRepository.parseTickerList(json)
        assertEquals(listOf("005930", "000660"), result)
    }

    @Test
    fun `parseTickerList handles invalid JSON gracefully`() {
        val result = StockGroupRepository.parseTickerList("not-json")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `buildGroup computes avgSignal from scoreMap`() {
        val scoreMap = mapOf("005930" to 0.8f, "000660" to 0.6f, "373220" to 0.7f)
        val group = StockGroupRepository.buildGroup(
            id = 1L,
            name = "테스트",
            type = GroupType.USER_THEME,
            tickers = listOf("005930", "000660", "373220"),
            scoreMap = scoreMap,
        )
        assertEquals(0.7f, group.avgSignal, 0.001f)
        assertEquals("005930", group.topSignalTicker)
        assertEquals(3, group.memberCount)
    }

    @Test
    fun `buildGroup returns neutral for tickers not in scoreMap`() {
        val group = StockGroupRepository.buildGroup(
            id = 1L,
            name = "없는종목",
            type = GroupType.KRX_SECTOR,
            tickers = listOf("999999"),
            scoreMap = emptyMap(),
        )
        assertEquals(0.5f, group.avgSignal)
    }

    @Test
    fun `buildGroup topSignalTicker picks highest score`() {
        val scoreMap = mapOf("A" to 0.3f, "B" to 0.9f, "C" to 0.5f)
        val group = StockGroupRepository.buildGroup(
            id = 1L,
            name = "Test",
            type = GroupType.USER_THEME,
            tickers = listOf("A", "B", "C"),
            scoreMap = scoreMap,
        )
        assertEquals("B", group.topSignalTicker)
    }
}
