package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.MarketDemarkRow
import com.tinyoscillator.core.util.DateFormats
import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * 시장 지수(KOSPI/KOSDAQ) 대상 DeMark TD Sequential 계산.
 *
 * 기존 CalcDemarkTDUseCase와 동일한 알고리즘이지만,
 * 개별 종목(DailyTrading) 대신 시장 지수(IndexDay)를 입력으로 받는다.
 *
 * TD Setup 규칙:
 * - Close[t] > Close[t-4] → tdSell +1, tdBuy = 0
 * - Close[t] < Close[t-4] → tdBuy +1, tdSell = 0
 * - Close[t] == Close[t-4] → 양쪽 리셋
 */
class CalcMarketDemarkUseCase {

    data class IndexDay(
        val date: String,       // yyyyMMdd
        val close: Double
    )

    private val fmt = DateFormats.yyyyMMdd

    fun execute(indexData: List<IndexDay>, periodType: DemarkPeriodType): List<MarketDemarkRow> {
        require(indexData.isNotEmpty()) { "지수 데이터가 비어있습니다" }

        val sorted = indexData.sortedBy { it.date }
        val data = when (periodType) {
            DemarkPeriodType.DAILY -> sorted
            DemarkPeriodType.WEEKLY -> resampleToWeekly(sorted)
        }

        require(data.size >= 5) { "DeMark TD 계산에 최소 5개 데이터가 필요합니다 (현재: ${data.size}개)" }

        val rows = mutableListOf<MarketDemarkRow>()
        var sellCount = 0
        var buyCount = 0

        for (i in data.indices) {
            if (i < 4) {
                rows.add(MarketDemarkRow(data[i].date, data[i].close, 0, 0))
            } else {
                val current = data[i].close
                val compare = data[i - 4].close

                when {
                    current > compare -> { sellCount++; buyCount = 0 }
                    current < compare -> { buyCount++; sellCount = 0 }
                    else -> { sellCount = 0; buyCount = 0 }
                }

                rows.add(MarketDemarkRow(data[i].date, data[i].close, sellCount, buyCount))
            }
        }

        return rows
    }

    internal fun resampleToWeekly(data: List<IndexDay>): List<IndexDay> {
        if (data.isEmpty()) return emptyList()

        return data
            .groupBy { day ->
                val date = LocalDate.parse(day.date, fmt)
                val year = date.get(IsoFields.WEEK_BASED_YEAR)
                val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                year * 100 + week
            }
            .toSortedMap()
            .map { (_, weekData) -> weekData.maxBy { it.date } }
    }
}
