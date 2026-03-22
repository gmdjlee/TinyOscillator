package com.tinyoscillator.presentation.settings

import org.junit.Assert.*
import org.junit.Test

class MarketCloseRefreshSettingsTest {

    @Test
    fun `default schedule time is 19 00 disabled`() {
        val schedule = MarketCloseRefreshScheduleTime()
        assertEquals(19, schedule.hour)
        assertEquals(0, schedule.minute)
        assertFalse(schedule.enabled)
    }

    @Test
    fun `custom schedule time preserves values`() {
        val schedule = MarketCloseRefreshScheduleTime(hour = 18, minute = 30, enabled = true)
        assertEquals(18, schedule.hour)
        assertEquals(30, schedule.minute)
        assertTrue(schedule.enabled)
    }

    @Test
    fun `schedule data class copy works correctly`() {
        val original = MarketCloseRefreshScheduleTime()
        val modified = original.copy(enabled = true, hour = 20)
        assertEquals(20, modified.hour)
        assertEquals(0, modified.minute)
        assertTrue(modified.enabled)
        assertFalse(original.enabled) // 원본 불변
    }

    @Test
    fun `schedule data class equality`() {
        val a = MarketCloseRefreshScheduleTime(19, 0, false)
        val b = MarketCloseRefreshScheduleTime(19, 0, false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `schedule data class inequality`() {
        val a = MarketCloseRefreshScheduleTime(19, 0, false)
        val b = MarketCloseRefreshScheduleTime(19, 0, true)
        assertNotEquals(a, b)
    }
}
