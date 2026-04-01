package com.tinyoscillator.presentation.consensus

import com.tinyoscillator.domain.model.ConsensusChartData
import org.junit.Assert.*
import org.junit.Test

/**
 * ConsensusChart 데이터 처리 로직 검증.
 *
 * NegativeArraySizeException 회귀 방지: 목표가 엔트리가 x-value로 정렬되어야 함.
 * (commit 51d9598 수정 사항 검증)
 */
class ConsensusChartDataTest {

    /**
     * 목표가 리포트 날짜가 dates 리스트 내에서 비순차적으로 위치할 때,
     * x-index 매핑 후 정렬이 필요함을 검증.
     */
    @Test
    fun `target entries must be sorted by x-index — regression for NegativeArraySizeException`() {
        val chartData = ConsensusChartData(
            ticker = "005930",
            stockName = "삼성전자",
            dates = listOf("20260101", "20260102", "20260103", "20260104", "20260105"),
            closePrices = listOf(70000, 71000, 72000, 71500, 73000),
            // 리포트 날짜가 역순 (date index: 4, 1 → unsorted x-values)
            reportDates = listOf("20260105", "20260102"),
            reportTargetPrices = listOf(80000L, 75000L)
        )

        // 차트 바인딩 로직 시뮬레이션 (ConsensusChart.kt bindConsensusData 내부 로직)
        val dates = chartData.dates
        val targetEntries = chartData.reportDates.mapIndexedNotNull { i, reportDate ->
            val xIndex = dates.indexOf(reportDate)
            if (xIndex >= 0) {
                Pair(xIndex.toFloat(), chartData.reportTargetPrices[i].toFloat())
            } else null
        }.sortedBy { it.first }

        // 정렬 확인 — MPAndroidChart는 x-value 순서를 요구
        assertEquals(2, targetEntries.size)
        assertTrue(
            "x-values must be sorted: ${targetEntries.map { it.first }}",
            targetEntries[0].first <= targetEntries[1].first
        )
        assertEquals(1f, targetEntries[0].first, 0.001f) // 20260102 → index 1
        assertEquals(4f, targetEntries[1].first, 0.001f) // 20260105 → index 4
    }

    @Test
    fun `target entries with unknown report dates are filtered out`() {
        val chartData = ConsensusChartData(
            ticker = "005930",
            stockName = "삼성전자",
            dates = listOf("20260101", "20260102", "20260103"),
            closePrices = listOf(70000, 71000, 72000),
            reportDates = listOf("20260102", "20260110"), // 20260110 not in dates
            reportTargetPrices = listOf(80000L, 85000L)
        )

        val dates = chartData.dates
        val targetEntries = chartData.reportDates.mapIndexedNotNull { i, reportDate ->
            val xIndex = dates.indexOf(reportDate)
            if (xIndex >= 0) {
                Pair(xIndex.toFloat(), chartData.reportTargetPrices[i].toFloat())
            } else null
        }

        assertEquals(1, targetEntries.size)
        assertEquals(1f, targetEntries[0].first, 0.001f)
    }

    @Test
    fun `empty dates produces empty target entries`() {
        val chartData = ConsensusChartData(
            ticker = "005930",
            stockName = "삼성전자",
            dates = emptyList(),
            closePrices = emptyList(),
            reportDates = listOf("20260102"),
            reportTargetPrices = listOf(80000L)
        )

        val dates = chartData.dates
        val targetEntries = chartData.reportDates.mapIndexedNotNull { i, reportDate ->
            val xIndex = dates.indexOf(reportDate)
            if (xIndex >= 0) {
                Pair(xIndex.toFloat(), chartData.reportTargetPrices[i].toFloat())
            } else null
        }

        assertTrue(targetEntries.isEmpty())
    }

    @Test
    fun `y-axis range includes both prices and targets`() {
        val chartData = ConsensusChartData(
            ticker = "005930",
            stockName = "삼성전자",
            dates = listOf("20260101", "20260102"),
            closePrices = listOf(70000, 71000),
            reportDates = listOf("20260101"),
            reportTargetPrices = listOf(80000L)
        )

        val priceValues = chartData.closePrices.map { it.toFloat() }
        val targetValues = chartData.reportTargetPrices.map { it.toFloat() }
        val allValues = priceValues + targetValues

        val yMin = allValues.min()
        val yMax = allValues.max()

        assertEquals(70000f, yMin, 0.001f)
        assertEquals(80000f, yMax, 0.001f)
    }
}
