package com.tinyoscillator.data.engine.macro

import com.tinyoscillator.core.api.BokEcosApiClient
import com.tinyoscillator.domain.model.EcosDataPoint
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BokEcosCollectorTest {

    private lateinit var mockApiClient: BokEcosApiClient
    private lateinit var collector: BokEcosCollector

    @Before
    fun setup() {
        mockApiClient = mockk()
        collector = BokEcosCollector(mockApiClient)
    }

    /**
     * 24개월 합성 데이터 생성 (202401~202512)
     */
    private fun makeSeries(baseValue: Double, growthPerMonth: Double): List<EcosDataPoint> {
        return (0 until 24).map { i ->
            val ym = if (i < 12) {
                String.format("2024%02d", i + 1)
            } else {
                String.format("2025%02d", i - 11)
            }
            EcosDataPoint(time = ym, value = baseValue + growthPerMonth * i)
        }
    }

    @Test
    fun `macroSignalVector returns unavailable when API key blank`() = runTest {
        val result = collector.macroSignalVector("", "20260301")
        assertNotNull(result.unavailableReason)
        assertEquals("NEUTRAL", result.macroEnv)
    }

    @Test
    fun `macroSignalVector returns unavailable when too few indicators`() = runTest {
        // Only 2 of 5 indicators return data
        coEvery { mockApiClient.fetchAll(any(), any(), any()) } returns mapOf(
            "base_rate" to makeSeries(3.5, 0.02),
            "m2" to makeSeries(3000.0, 50.0),
            "iip" to emptyList(),
            "usd_krw" to emptyList(),
            "cpi" to emptyList()
        )
        val result = collector.macroSignalVector("test_key", "20260301")
        assertNotNull(result.unavailableReason)
        assertTrue(result.unavailableReason!!.contains("데이터 부족"))
    }

    @Test
    fun `macroSignalVector returns valid result with all 5 indicators`() = runTest {
        coEvery { mockApiClient.fetchAll(any(), any(), any()) } returns mapOf(
            "base_rate" to makeSeries(3.0, 0.05),  // 3.0 → 4.15
            "m2" to makeSeries(3000.0, 50.0),       // 3000 → 4150
            "iip" to makeSeries(100.0, 1.0),         // 100 → 123
            "usd_krw" to makeSeries(1300.0, 5.0),    // 1300 → 1415
            "cpi" to makeSeries(105.0, 0.3)          // 105 → 111.9
        )
        val result = collector.macroSignalVector("test_key", "20260101")
        assertNull(result.unavailableReason)
        // macroEnv is empty (set by overlay)
        assertEquals("", result.macroEnv)
    }

    @Test
    fun `pointsToMonthMap applies ffill for missing months`() {
        val points = listOf(
            EcosDataPoint("202401", 100.0),
            EcosDataPoint("202403", 102.0)  // 202402 missing
        )
        val result = collector.pointsToMonthMap(points)
        assertEquals(100.0, result["202401"]!!, 0.001)
        assertEquals(100.0, result["202402"]!!, 0.001)  // ffilled
        assertEquals(102.0, result["202403"]!!, 0.001)
    }

    @Test
    fun `pointsToMonthMap does not ffill beyond MAX_FFILL_GAP`() {
        val points = listOf(
            EcosDataPoint("202401", 100.0),
            EcosDataPoint("202406", 106.0)  // 4 months gap
        )
        val result = collector.pointsToMonthMap(points)
        assertTrue(result.containsKey("202401"))
        assertTrue(result.containsKey("202402"))  // gap 1
        assertTrue(result.containsKey("202403"))  // gap 2
        assertTrue(result.containsKey("202404"))  // gap 3
        assertFalse(result.containsKey("202405")) // gap 4 — beyond limit
        assertTrue(result.containsKey("202406"))
    }

    @Test
    fun `computeYoyForMonth calculates base_rate as absolute change`() {
        val seriesMap = mapOf(
            "base_rate" to mapOf("202401" to 3.0, "202501" to 3.5)
        )
        val yoy = collector.computeYoyForMonth(seriesMap, "202501")
        assertEquals(0.5, yoy["base_rate"]!!, 0.001) // 3.5 - 3.0 = +0.5pp
    }

    @Test
    fun `computeYoyForMonth calculates percentage change for non-rate indicators`() {
        val seriesMap = mapOf(
            "m2" to mapOf("202401" to 3000.0, "202501" to 3300.0)
        )
        val yoy = collector.computeYoyForMonth(seriesMap, "202501")
        assertEquals(10.0, yoy["m2"]!!, 0.001) // (3300-3000)/3000*100 = 10%
    }

    @Test
    fun `computeYoyForMonth handles zero previous value gracefully`() {
        val seriesMap = mapOf(
            "iip" to mapOf("202401" to 0.0, "202501" to 5.0)
        )
        val yoy = collector.computeYoyForMonth(seriesMap, "202501")
        assertEquals(0.0, yoy["iip"]!!, 0.001) // division by zero → 0
    }

    @Test
    fun `computeYoyForMonth falls back to closest month if exact month missing`() {
        // No data for 202501, but 202412 exists
        val seriesMap = mapOf(
            "cpi" to mapOf("202401" to 100.0, "202412" to 103.0)
        )
        val yoy = collector.computeYoyForMonth(seriesMap, "202501")
        // Should use 202412 as proxy for 202501, and 202401 for prev year
        assertEquals(3.0, yoy["cpi"]!!, 0.001)
    }

    @Test
    fun `pointsToMonthMap returns empty for empty input`() {
        val result = collector.pointsToMonthMap(emptyList())
        assertTrue(result.isEmpty())
    }
}
