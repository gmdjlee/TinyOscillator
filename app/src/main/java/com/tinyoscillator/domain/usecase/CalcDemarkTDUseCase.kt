package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorConfig.Companion.MARKET_CAP_DIVISOR
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

/**
 * DeMark TD Sequential 계산 UseCase
 *
 * TD Setup 규칙:
 * - Close[t] > Close[t-4] → tdSell +1 (매도 피로 누적), tdBuy = 0
 * - Close[t] < Close[t-4] → tdBuy +1 (매수 피로 누적), tdSell = 0
 * - Close[t] == Close[t-4] → 양쪽 모두 0 리셋
 *
 * 최소 5개 데이터 필요 (t-4 비교).
 */
class CalcDemarkTDUseCase {

    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun execute(dailyData: List<DailyTrading>, periodType: DemarkPeriodType): List<DemarkTDRow> {
        require(dailyData.isNotEmpty()) { "일별 데이터가 비어있습니다" }

        val data = when (periodType) {
            DemarkPeriodType.DAILY -> dailyData
            DemarkPeriodType.WEEKLY -> resampleToWeekly(dailyData)
        }

        require(data.size >= 5) { "DeMark TD 계산에 최소 5개 데이터가 필요합니다 (현재: ${data.size}개)" }

        val rows = mutableListOf<DemarkTDRow>()
        var sellCount = 0
        var buyCount = 0

        for (i in data.indices) {
            if (i < 4) {
                // t-4 비교 불가 → 카운트 0
                rows.add(
                    DemarkTDRow(
                        date = data[i].date,
                        closePrice = data[i].closePrice,
                        marketCapTril = data[i].marketCap / MARKET_CAP_DIVISOR,
                        tdSellCount = 0,
                        tdBuyCount = 0
                    )
                )
            } else {
                val current = data[i].closePrice
                val compare = data[i - 4].closePrice

                when {
                    current > compare -> {
                        sellCount++
                        buyCount = 0
                    }
                    current < compare -> {
                        buyCount++
                        sellCount = 0
                    }
                    else -> {
                        // 동일 종가 → 양쪽 리셋
                        sellCount = 0
                        buyCount = 0
                    }
                }

                rows.add(
                    DemarkTDRow(
                        date = data[i].date,
                        closePrice = data[i].closePrice,
                        marketCapTril = data[i].marketCap / MARKET_CAP_DIVISOR,
                        tdSellCount = sellCount,
                        tdBuyCount = buyCount
                    )
                )
            }
        }

        return rows
    }

    /**
     * 일봉 → 주봉 리샘플링 (ISO week 기준).
     * 각 주의 마지막 거래일 데이터를 사용.
     */
    internal fun resampleToWeekly(data: List<DailyTrading>): List<DailyTrading> {
        if (data.isEmpty()) return emptyList()

        return data
            .groupBy { daily ->
                val date = LocalDate.parse(daily.date, fmt)
                val year = date.get(IsoFields.WEEK_BASED_YEAR)
                val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                year * 100 + week  // e.g., 202501
            }
            .toSortedMap()
            .map { (_, weekData) ->
                // 주간 마지막 거래일의 데이터 사용
                weekData.maxBy { it.date }
            }
    }
}
