package com.tinyoscillator.presentation.watchlist

import com.tinyoscillator.core.testing.SyntheticScreeningData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistSortTest {

    private val entries = SyntheticScreeningData.watchlistEntries(10)

    @Test
    fun `sort by signal descending puts highest first`() {
        val sorted = entries.sortedByDescending { it.signalScore }
        assertTrue(sorted.first().signalScore >= sorted.last().signalScore)
    }

    @Test
    fun `sort by signal ascending puts lowest first`() {
        val sorted = entries.sortedBy { it.signalScore }
        assertTrue(sorted.first().signalScore <= sorted.last().signalScore)
    }

    @Test
    fun `sort by custom order uses sortOrder field`() {
        val sorted = entries.sortedBy { it.sortOrder }
        val orders = sorted.map { it.sortOrder }
        assertEquals(orders, orders.sorted())
    }

    @Test
    fun `sort by price change descending`() {
        val sorted = entries.sortedByDescending { it.priceChange }
        assertTrue(sorted.first().priceChange >= sorted.last().priceChange)
    }

    @Test
    fun `reorder moves item to target position`() {
        val mutable = entries.toMutableList()
        val moved = mutable.removeAt(0)
        mutable.add(3, moved)
        assertEquals(moved.id, mutable[3].id)
    }

    @Test
    fun `reorder preserves total count`() {
        val mutable = entries.toMutableList()
        val moved = mutable.removeAt(2)
        mutable.add(5, moved)
        assertEquals(entries.size, mutable.size)
    }
}
