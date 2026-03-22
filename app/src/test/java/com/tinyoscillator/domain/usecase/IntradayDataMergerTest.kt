package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.RealtimeSupplyData
import org.junit.Assert.*
import org.junit.Test
import com.tinyoscillator.core.util.DateFormats
import java.time.LocalDate

/**
 * IntradayDataMerger 단위 테스트
 *
 * 장중 실시간 수급 데이터 병합 로직 검증
 */
class IntradayDataMergerTest {

    private val dateFormatter = DateFormats.yyyyMMdd
    private val todayStr = LocalDate.now().format(dateFormatter)
    private val yesterdayStr = LocalDate.now().minusDays(1).format(dateFormatter)

    private fun createIntradayData(netBuyAmountMillionWon: Long = 500L) = RealtimeSupplyData(
        ticker = "005930",
        name = "삼성전자",
        currentPrice = 70000L,
        netBuyAmount = netBuyAmountMillionWon,
        buyAmount = 1000L,
        sellAmount = 500L,
        netBuyQuantity = 100L,
        accumulatedVolume = 50000L,
        fetchedAt = System.currentTimeMillis()
    )

    private fun createDailyTrading(
        date: String,
        marketCap: Long = 1_000_000_000_000L,
        foreignNetBuy: Long = 100_000_000L,
        instNetBuy: Long = 50_000_000L,
        closePrice: Int = 70000
    ) = DailyTrading(
        date = date,
        marketCap = marketCap,
        foreignNetBuy = foreignNetBuy,
        instNetBuy = instNetBuy,
        closePrice = closePrice
    )

    // ── 1. baseData가 비어있으면 그대로 반환 ──

    @Test
    fun `빈 baseData에 merge하면 빈 리스트 반환`() {
        val intradayData = createIntradayData()

        val result = IntradayDataMerger.merge(emptyList(), intradayData)

        assertTrue("빈 리스트 그대로 반환", result.isEmpty())
    }

    // ── 2. 최신 날짜가 오늘이면 마지막 항목 교체 ──

    @Test
    fun `최신 날짜가 오늘이면 foreignNetBuy를 교체한다`() {
        val baseData = listOf(
            createDailyTrading(yesterdayStr),
            createDailyTrading(todayStr, foreignNetBuy = 200_000_000L, instNetBuy = 100_000_000L)
        )
        val intradayData = createIntradayData(netBuyAmountMillionWon = 300L)

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("리스트 크기 유지", 2, result.size)
        assertEquals("foreignNetBuy 교체 (300백만원 → 원)", 300_000_000L, result.last().foreignNetBuy)
        assertEquals("첫 항목은 변경 없음", baseData[0].foreignNetBuy, result[0].foreignNetBuy)
    }

    @Test
    fun `최신 날짜가 오늘이면 instNetBuy는 0으로 설정된다`() {
        val baseData = listOf(
            createDailyTrading(todayStr, instNetBuy = 999_000_000L)
        )
        val intradayData = createIntradayData()

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("instNetBuy는 항상 0", 0L, result.last().instNetBuy)
    }

    @Test
    fun `최신 날짜가 오늘이면 marketCap과 closePrice는 유지된다`() {
        val baseData = listOf(
            createDailyTrading(todayStr, marketCap = 5_000_000_000_000L, closePrice = 80000)
        )
        val intradayData = createIntradayData()

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("marketCap 유지", 5_000_000_000_000L, result.last().marketCap)
        assertEquals("closePrice 유지", 80000, result.last().closePrice)
    }

    // ── 3. 최신 날짜가 과거이면 오늘 데이터 추가 ──

    @Test
    fun `최신 날짜가 과거이면 오늘 날짜로 새 항목 추가`() {
        val baseData = listOf(
            createDailyTrading(yesterdayStr, marketCap = 2_000_000_000_000L, closePrice = 65000)
        )
        val intradayData = createIntradayData(netBuyAmountMillionWon = 150L)

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("리스트 크기 1 증가", 2, result.size)
        assertEquals("추가된 항목의 날짜는 오늘", todayStr, result.last().date)
        assertEquals("foreignNetBuy 변환 (150백만원 → 원)", 150_000_000L, result.last().foreignNetBuy)
        assertEquals("instNetBuy는 0", 0L, result.last().instNetBuy)
        assertEquals("marketCap은 전일 값 사용", 2_000_000_000_000L, result.last().marketCap)
        assertEquals("closePrice은 전일 값 사용", 65000, result.last().closePrice)
    }

    @Test
    fun `과거 날짜 추가 시 기존 데이터는 변경되지 않는다`() {
        val baseData = listOf(
            createDailyTrading("20260301"),
            createDailyTrading(yesterdayStr)
        )
        val intradayData = createIntradayData()

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("기존 첫 항목 유지", baseData[0], result[0])
        assertEquals("기존 두번째 항목 유지", baseData[1], result[1])
        assertEquals("새 항목 추가됨", 3, result.size)
    }

    // ── 4. 단위 변환 (백만원 → 원) ──

    @Test
    fun `netBuyAmount 백만원을 원으로 변환한다`() {
        val baseData = listOf(createDailyTrading(todayStr))
        val intradayData = createIntradayData(netBuyAmountMillionWon = 1_234L)

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("1,234백만원 → 1,234,000,000원", 1_234_000_000L, result.last().foreignNetBuy)
    }

    @Test
    fun `음수 netBuyAmount도 정상 변환한다`() {
        val baseData = listOf(createDailyTrading(todayStr))
        val intradayData = createIntradayData(netBuyAmountMillionWon = -500L)

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("음수 변환: -500백만원 → -500,000,000원", -500_000_000L, result.last().foreignNetBuy)
    }

    @Test
    fun `netBuyAmount가 0이면 0원`() {
        val baseData = listOf(createDailyTrading(todayStr))
        val intradayData = createIntradayData(netBuyAmountMillionWon = 0L)

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("0백만원 → 0원", 0L, result.last().foreignNetBuy)
    }

    // ── 5. instNetBuy는 항상 0 ──

    @Test
    fun `교체 모드에서 instNetBuy는 항상 0이다`() {
        val baseData = listOf(
            createDailyTrading(todayStr, instNetBuy = 999_999_999L)
        )
        val intradayData = createIntradayData()

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("교체 시 instNetBuy = 0", 0L, result.last().instNetBuy)
    }

    @Test
    fun `추가 모드에서 instNetBuy는 항상 0이다`() {
        val baseData = listOf(
            createDailyTrading(yesterdayStr, instNetBuy = 888_888_888L)
        )
        val intradayData = createIntradayData()

        val result = IntradayDataMerger.merge(baseData, intradayData)

        assertEquals("추가 시 instNetBuy = 0", 0L, result.last().instNetBuy)
    }
}
