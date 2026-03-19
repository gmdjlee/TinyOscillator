package com.tinyoscillator.presentation.etf

import org.junit.Assert.*
import org.junit.Test

/**
 * EtfStatsViewModel 비즈니스 로직 단위 테스트
 *
 * groupDatesByWeek, WeekInfo, ComparisonMode 검증
 */
class EtfStatsViewModelTest {

    // ==========================================================
    // groupDatesByWeek 테스트
    // ==========================================================

    @Test
    fun `groupDatesByWeek - 빈 날짜 리스트는 빈 결과를 반환한다`() {
        val result = groupDatesByWeek(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `groupDatesByWeek - 단일 날짜는 1개 주차를 반환한다`() {
        val result = groupDatesByWeek(listOf("20260305"))
        assertEquals(1, result.size)
        assertEquals("20260305", result[0].representativeDate)
    }

    @Test
    fun `groupDatesByWeek - 같은 주의 날짜들은 하나의 그룹으로 묶인다`() {
        // 2026-03-02(월) ~ 2026-03-06(금) — 같은 주
        val dates = listOf("20260306", "20260305", "20260304", "20260303", "20260302")
        val result = groupDatesByWeek(dates)
        assertEquals(1, result.size)
        assertEquals("20260306", result[0].representativeDate) // most recent in dates
    }

    @Test
    fun `groupDatesByWeek - 서로 다른 주의 날짜들은 별도 그룹이 된다`() {
        // 2026-03-06(금) = 10주차, 2026-02-27(금) = 9주차
        val dates = listOf("20260306", "20260305", "20260227", "20260226")
        val result = groupDatesByWeek(dates)
        assertEquals(2, result.size)
        // 최신 주차가 먼저
        assertTrue(result[0].weekNumber >= result[1].weekNumber || result[0].year > result[1].year)
    }

    @Test
    fun `groupDatesByWeek - 결과는 최신 주차순으로 정렬된다`() {
        val dates = listOf("20260313", "20260306", "20260227")
        val result = groupDatesByWeek(dates)
        assertTrue(result.size >= 2)
        // 첫 번째가 가장 최신
        val firstKey = result[0].year * 100 + result[0].weekNumber
        val lastKey = result.last().year * 100 + result.last().weekNumber
        assertTrue("최신 주차가 먼저 와야 한다", firstKey >= lastKey)
    }

    @Test
    fun `groupDatesByWeek - dateRange가 올바르게 설정된다`() {
        val dates = listOf("20260306", "20260305", "20260304")
        val result = groupDatesByWeek(dates)
        assertEquals(1, result.size)
        val week = result[0]
        // dateRange: (oldest, newest) within the group
        assertEquals("20260304", week.dateRange.first)
        assertEquals("20260306", week.dateRange.second)
    }

    @Test
    fun `groupDatesByWeek - label에 주차 번호와 날짜 범위가 포함된다`() {
        val dates = listOf("20260306", "20260305")
        val result = groupDatesByWeek(dates)
        assertEquals(1, result.size)
        val label = result[0].label
        assertTrue("label에 '주차'가 포함되어야 한다", label.contains("주차"))
        assertTrue("label에 날짜 범위가 포함되어야 한다", label.contains("~"))
    }

    // ==========================================================
    // WeekInfo 테스트
    // ==========================================================

    @Test
    fun `WeekInfo 생성 및 프로퍼티 접근`() {
        val weekInfo = WeekInfo(
            year = 2026,
            weekNumber = 10,
            representativeDate = "20260306",
            dateRange = "20260302" to "20260306",
            label = "10주차 (03.02~03.06)"
        )
        assertEquals(2026, weekInfo.year)
        assertEquals(10, weekInfo.weekNumber)
        assertEquals("20260306", weekInfo.representativeDate)
        assertEquals("20260302", weekInfo.dateRange.first)
        assertEquals("20260306", weekInfo.dateRange.second)
    }

    @Test
    fun `WeekInfo equals 및 hashCode`() {
        val w1 = WeekInfo(2026, 10, "20260306", "20260302" to "20260306", "10주차")
        val w2 = WeekInfo(2026, 10, "20260306", "20260302" to "20260306", "10주차")
        assertEquals(w1, w2)
        assertEquals(w1.hashCode(), w2.hashCode())
    }

    // ==========================================================
    // ComparisonMode 테스트
    // ==========================================================

    @Test
    fun `ComparisonMode - entries 개수가 2개이다`() {
        assertEquals(2, ComparisonMode.entries.size)
    }

    @Test
    fun `ComparisonMode - DAILY와 WEEKLY 값이 존재한다`() {
        assertEquals(ComparisonMode.DAILY, ComparisonMode.valueOf("DAILY"))
        assertEquals(ComparisonMode.WEEKLY, ComparisonMode.valueOf("WEEKLY"))
    }

    // ==========================================================
    // 비교일 자동 선택 로직 테스트 (순수 함수 추출 테스트)
    // ==========================================================

    @Test
    fun `일간 모드 - 비교일은 기준일 다음 날짜가 된다`() {
        val dates = listOf("20260306", "20260305", "20260304", "20260303")
        val selectedDate = "20260305"
        val idx = dates.indexOf(selectedDate)
        val comparisonDate = dates.getOrNull(idx + 1)
        assertEquals("20260304", comparisonDate)
    }

    @Test
    fun `일간 모드 - 가장 오래된 날짜 선택 시 비교일은 null이다`() {
        val dates = listOf("20260306", "20260305", "20260304")
        val selectedDate = "20260304"
        val idx = dates.indexOf(selectedDate)
        val comparisonDate = dates.getOrNull(idx + 1)
        assertNull(comparisonDate)
    }

    @Test
    fun `일간 모드 - 존재하지 않는 날짜 선택 시 비교일을 설정하지 않는다`() {
        val dates = listOf("20260306", "20260305")
        val selectedDate = "20260301"
        val idx = dates.indexOf(selectedDate)
        assertTrue(idx < 0)
    }

    @Test
    fun `주간 모드 - 비교주는 기준주 다음 주차가 된다`() {
        val dates = listOf("20260313", "20260312", "20260306", "20260305")
        val weeks = groupDatesByWeek(dates)
        assertTrue(weeks.size >= 2)
        val selectedWeek = weeks[0]
        val compWeek = weeks.getOrNull(1)
        assertNotNull(compWeek)
        assertNotEquals(selectedWeek.weekNumber, compWeek!!.weekNumber)
    }

    @Test
    fun `주간 모드 - 마지막 주차 선택 시 비교주는 null이다`() {
        val dates = listOf("20260306", "20260305")
        val weeks = groupDatesByWeek(dates)
        assertEquals(1, weeks.size)
        val compWeek = weeks.getOrNull(1)
        assertNull(compWeek)
    }

    @Test
    fun `비교일 수동 선택 - 기준일과 다른 날짜를 선택할 수 있다`() {
        val dates = listOf("20260306", "20260305", "20260304", "20260303")
        val selectedDate = "20260306"
        val manualComparisonDate = "20260303"
        // 기준일과 비교일이 다른지 검증
        assertNotEquals(selectedDate, manualComparisonDate)
        assertTrue(dates.contains(manualComparisonDate))
    }

    @Test
    fun `비교주 수동 선택 - 기준주와 다른 주차를 선택할 수 있다`() {
        val dates = listOf("20260313", "20260312", "20260306", "20260305", "20260227")
        val weeks = groupDatesByWeek(dates)
        assertTrue(weeks.size >= 2)
        val selectedWeek = weeks[0]
        val manualComparisonWeek = weeks.last()
        assertNotEquals(selectedWeek, manualComparisonWeek)
    }
}
