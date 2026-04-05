package com.tinyoscillator.domain.screener

import com.tinyoscillator.core.testing.SyntheticScreeningData
import com.tinyoscillator.data.datasource.ScreenerDataSource.Companion.meetsFilter
import com.tinyoscillator.domain.model.ScreenerFilter
import com.tinyoscillator.domain.model.ScreenerResultItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenerFilterTest {

    private fun item(
        signal: Float = 0.7f,
        pbr: Float = 1.5f,
        mkcap: Long = 5000L,
        foreign: Float = 0.3f,
        vol: Float = 1.5f,
    ) = ScreenerResultItem("A", "A종목", signal, pbr, mkcap, foreign, vol, "IT")

    @Test
    fun `default filter passes high signal item`() {
        assertTrue(meetsFilter(item(signal = 0.75f), ScreenerFilter()))
    }

    @Test
    fun `low signal item rejected by default filter`() {
        assertFalse(meetsFilter(item(signal = 0.40f), ScreenerFilter()))
    }

    @Test
    fun `market cap filter rejects small cap`() {
        val f = ScreenerFilter(minMarketCapBil = 10_000L)
        assertFalse(meetsFilter(item(mkcap = 5_000L), f))
    }

    @Test
    fun `PBR max filter rejects high PBR`() {
        val f = ScreenerFilter(maxPbr = 2.0f)
        assertFalse(meetsFilter(item(pbr = 3.5f), f))
    }

    @Test
    fun `foreign ratio min filter rejects low foreign`() {
        val f = ScreenerFilter(minForeignRatio = 0.5f)
        assertFalse(meetsFilter(item(foreign = 0.3f), f))
    }

    @Test
    fun `volume ratio min filter rejects low volume`() {
        val f = ScreenerFilter(minVolumeRatio = 2.0f)
        assertFalse(meetsFilter(item(vol = 1.5f), f))
    }

    @Test
    fun `item meeting all conditions passes combined filter`() {
        val f = ScreenerFilter(
            minSignalScore = 0.6f,
            maxSignalScore = 0.9f,
            minMarketCapBil = 1000L,
            maxMarketCapBil = 50000L,
            maxPbr = 3.0f,
            minForeignRatio = 0.2f,
            minVolumeRatio = 1.0f,
        )
        assertTrue(meetsFilter(item(), f))
    }

    @Test
    fun `sort by signal score descending`() {
        val items = SyntheticScreeningData.screenerItems(10)
        val sorted = items.sortedByDescending { it.signalScore }
        assertTrue(sorted.first().signalScore >= sorted.last().signalScore)
    }

    @Test
    fun `sort by PBR ascending (lowest first)`() {
        val items = SyntheticScreeningData.screenerItems(10)
        val sorted = items.sortedBy { it.pbr }
        assertTrue(sorted.first().pbr <= sorted.last().pbr)
    }
}
