package com.tinyoscillator.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * ETF 통계 도메인 모델 단위 테스트
 *
 * DateRange enum, AmountRankingRow, CashDepositRow, StockInEtfRow,
 * StockSearchResult, ChangeType, StockChange, AmountRankingItem,
 * HoldingTimeSeries, StockAggregatedTimePoint 검증
 */
class EtfStatModelsTest {

    // ==========================================================
    // DateRange enum 테스트
    // ==========================================================

    @Test
    fun `DateRange - 각 옵션의 label이 올바르다`() {
        assertEquals("1주", DateRange.WEEK_1.label)
        assertEquals("1개월", DateRange.MONTH_1.label)
        assertEquals("3개월", DateRange.MONTH_3.label)
        assertEquals("6개월", DateRange.MONTH_6.label)
        assertEquals("1년", DateRange.YEAR_1.label)
        assertEquals("전체", DateRange.ALL.label)
    }

    @Test
    fun `DateRange - 각 옵션의 days가 올바르다`() {
        assertEquals(7, DateRange.WEEK_1.days)
        assertEquals(30, DateRange.MONTH_1.days)
        assertEquals(90, DateRange.MONTH_3.days)
        assertEquals(180, DateRange.MONTH_6.days)
        assertEquals(365, DateRange.YEAR_1.days)
        assertEquals(Int.MAX_VALUE, DateRange.ALL.days)
    }

    @Test
    fun `DateRange - entries 개수가 6개이다`() {
        assertEquals(6, DateRange.entries.size)
    }

    @Test
    fun `DateRange - valueOf로 각 값을 조회할 수 있다`() {
        assertEquals(DateRange.WEEK_1, DateRange.valueOf("WEEK_1"))
        assertEquals(DateRange.MONTH_1, DateRange.valueOf("MONTH_1"))
        assertEquals(DateRange.MONTH_3, DateRange.valueOf("MONTH_3"))
        assertEquals(DateRange.MONTH_6, DateRange.valueOf("MONTH_6"))
        assertEquals(DateRange.YEAR_1, DateRange.valueOf("YEAR_1"))
        assertEquals(DateRange.ALL, DateRange.valueOf("ALL"))
    }

    // ==========================================================
    // AmountRankingRow 테스트
    // ==========================================================

    @Test
    fun `AmountRankingRow 생성 및 프로퍼티 접근`() {
        val row = AmountRankingRow(
            stock_ticker = "005930",
            stock_name = "삼성전자",
            totalAmount = 1_000_000_000L,
            etfCount = 15
        )
        assertEquals("005930", row.stock_ticker)
        assertEquals("삼성전자", row.stock_name)
        assertEquals(1_000_000_000L, row.totalAmount)
        assertEquals(15, row.etfCount)
    }

    @Test
    fun `AmountRankingRow equals 및 hashCode`() {
        val r1 = AmountRankingRow("005930", "삼성전자", 1000L, 5)
        val r2 = AmountRankingRow("005930", "삼성전자", 1000L, 5)
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun `AmountRankingRow copy`() {
        val original = AmountRankingRow("005930", "삼성전자", 1000L, 5)
        val copied = original.copy(totalAmount = 2000L)
        assertEquals(2000L, copied.totalAmount)
        assertEquals(original.stock_ticker, copied.stock_ticker)
    }

    // ==========================================================
    // CashDepositRow 테스트
    // ==========================================================

    @Test
    fun `CashDepositRow 생성 및 프로퍼티 접근`() {
        val row = CashDepositRow(
            date = "20240101",
            totalAmount = 500_000_000L,
            etfCount = 10
        )
        assertEquals("20240101", row.date)
        assertEquals(500_000_000L, row.totalAmount)
        assertEquals(10, row.etfCount)
    }

    @Test
    fun `CashDepositRow equals`() {
        val r1 = CashDepositRow("20240101", 500L, 10)
        val r2 = CashDepositRow("20240101", 500L, 10)
        assertEquals(r1, r2)
    }

    // ==========================================================
    // StockInEtfRow 테스트
    // ==========================================================

    @Test
    fun `StockInEtfRow 생성 및 프로퍼티 접근`() {
        val row = StockInEtfRow(
            etf_ticker = "069500",
            etfName = "KODEX 200",
            stock_ticker = "005930",
            stock_name = "삼성전자",
            weight = 25.5,
            shares = 1000L,
            amount = 75_000_000L,
            date = "20240101"
        )
        assertEquals("069500", row.etf_ticker)
        assertEquals("KODEX 200", row.etfName)
        assertEquals("005930", row.stock_ticker)
        assertEquals("삼성전자", row.stock_name)
        assertEquals(25.5, row.weight!!, 1e-10)
        assertEquals(1000L, row.shares)
        assertEquals(75_000_000L, row.amount)
        assertEquals("20240101", row.date)
    }

    @Test
    fun `StockInEtfRow - weight가 null일 수 있다`() {
        val row = StockInEtfRow(
            etf_ticker = "069500",
            etfName = "KODEX 200",
            stock_ticker = "005930",
            stock_name = "삼성전자",
            weight = null,
            shares = 1000L,
            amount = 75_000_000L,
            date = "20240101"
        )
        assertNull(row.weight)
    }

    // ==========================================================
    // StockSearchResult 테스트
    // ==========================================================

    @Test
    fun `StockSearchResult 생성 및 프로퍼티 접근`() {
        val result = StockSearchResult(
            stock_ticker = "005930",
            stock_name = "삼성전자"
        )
        assertEquals("005930", result.stock_ticker)
        assertEquals("삼성전자", result.stock_name)
    }

    @Test
    fun `StockSearchResult equals`() {
        val r1 = StockSearchResult("005930", "삼성전자")
        val r2 = StockSearchResult("005930", "삼성전자")
        assertEquals(r1, r2)
    }

    // ==========================================================
    // ChangeType enum 테스트
    // ==========================================================

    @Test
    fun `ChangeType - entries 개수가 4개이다`() {
        assertEquals(4, ChangeType.entries.size)
    }

    @Test
    fun `ChangeType - 각 값 확인`() {
        assertEquals(ChangeType.NEW, ChangeType.valueOf("NEW"))
        assertEquals(ChangeType.REMOVED, ChangeType.valueOf("REMOVED"))
        assertEquals(ChangeType.INCREASED, ChangeType.valueOf("INCREASED"))
        assertEquals(ChangeType.DECREASED, ChangeType.valueOf("DECREASED"))
    }

    // ==========================================================
    // StockChange 테스트
    // ==========================================================

    @Test
    fun `StockChange 생성 및 프로퍼티 접근`() {
        val change = StockChange(
            stockTicker = "005930",
            stockName = "삼성전자",
            etfTicker = "069500",
            etfName = "KODEX 200",
            previousWeight = 20.0,
            currentWeight = 25.5,
            previousAmount = 50_000_000L,
            currentAmount = 75_000_000L,
            changeType = ChangeType.INCREASED
        )
        assertEquals("005930", change.stockTicker)
        assertEquals(ChangeType.INCREASED, change.changeType)
        assertEquals(20.0, change.previousWeight!!, 1e-10)
        assertEquals(25.5, change.currentWeight!!, 1e-10)
    }

    @Test
    fun `StockChange - NEW 타입은 previousWeight가 null이다`() {
        val change = StockChange(
            stockTicker = "005930",
            stockName = "삼성전자",
            etfTicker = "069500",
            etfName = "KODEX 200",
            previousWeight = null,
            currentWeight = 5.0,
            previousAmount = 0L,
            currentAmount = 10_000_000L,
            changeType = ChangeType.NEW
        )
        assertNull(change.previousWeight)
        assertEquals(ChangeType.NEW, change.changeType)
    }

    @Test
    fun `StockChange - REMOVED 타입은 currentWeight가 null이다`() {
        val change = StockChange(
            stockTicker = "005930",
            stockName = "삼성전자",
            etfTicker = "069500",
            etfName = "KODEX 200",
            previousWeight = 5.0,
            currentWeight = null,
            previousAmount = 10_000_000L,
            currentAmount = 0L,
            changeType = ChangeType.REMOVED
        )
        assertNull(change.currentWeight)
        assertEquals(ChangeType.REMOVED, change.changeType)
    }

    // ==========================================================
    // AmountRankingItem 테스트
    // ==========================================================

    @Test
    fun `AmountRankingItem 생성 및 프로퍼티 접근`() {
        val item = AmountRankingItem(
            rank = 1,
            stockTicker = "005930",
            stockName = "삼성전자",
            totalAmountBillion = 150.5,
            etfCount = 20,
            newCount = 2,
            increasedCount = 5,
            decreasedCount = 3,
            removedCount = 1
        )
        assertEquals(1, item.rank)
        assertEquals("005930", item.stockTicker)
        assertEquals(150.5, item.totalAmountBillion, 1e-10)
        assertEquals(20, item.etfCount)
        assertEquals(2, item.newCount)
        assertEquals(5, item.increasedCount)
        assertEquals(3, item.decreasedCount)
        assertEquals(1, item.removedCount)
    }

    @Test
    fun `AmountRankingItem - 기본값은 0이다`() {
        val item = AmountRankingItem(
            rank = 1,
            stockTicker = "005930",
            stockName = "삼성전자",
            totalAmountBillion = 100.0,
            etfCount = 10
        )
        assertEquals(0, item.newCount)
        assertEquals(0, item.increasedCount)
        assertEquals(0, item.decreasedCount)
        assertEquals(0, item.removedCount)
    }

    // ==========================================================
    // HoldingTimeSeries 테스트
    // ==========================================================

    @Test
    fun `HoldingTimeSeries 생성 및 프로퍼티 접근`() {
        val ts = HoldingTimeSeries(
            date = "20240101",
            weight = 25.5,
            amount = 75_000_000L
        )
        assertEquals("20240101", ts.date)
        assertEquals(25.5, ts.weight!!, 1e-10)
        assertEquals(75_000_000L, ts.amount)
    }

    @Test
    fun `HoldingTimeSeries - weight가 null일 수 있다`() {
        val ts = HoldingTimeSeries(date = "20240101", weight = null, amount = 100L)
        assertNull(ts.weight)
    }

    // ==========================================================
    // StockAggregatedTimePoint 테스트
    // ==========================================================

    @Test
    fun `StockAggregatedTimePoint 생성 및 프로퍼티 접근`() {
        val tp = StockAggregatedTimePoint(
            date = "20240101",
            totalAmount = 500_000_000L,
            etfCount = 15,
            maxWeight = 30.0,
            avgWeight = 15.5
        )
        assertEquals("20240101", tp.date)
        assertEquals(500_000_000L, tp.totalAmount)
        assertEquals(15, tp.etfCount)
        assertEquals(30.0, tp.maxWeight!!, 1e-10)
        assertEquals(15.5, tp.avgWeight!!, 1e-10)
    }

    @Test
    fun `StockAggregatedTimePoint - weight 필드가 null일 수 있다`() {
        val tp = StockAggregatedTimePoint(
            date = "20240101",
            totalAmount = 100L,
            etfCount = 1,
            maxWeight = null,
            avgWeight = null
        )
        assertNull(tp.maxWeight)
        assertNull(tp.avgWeight)
    }

    @Test
    fun `StockAggregatedTimePoint equals`() {
        val t1 = StockAggregatedTimePoint("20240101", 100L, 5, 10.0, 5.0)
        val t2 = StockAggregatedTimePoint("20240101", 100L, 5, 10.0, 5.0)
        assertEquals(t1, t2)
        assertEquals(t1.hashCode(), t2.hashCode())
    }

    // ==========================================================
    // WeightTrend enum 테스트
    // ==========================================================

    @Test
    fun `WeightTrend - entries 개수가 4개이다`() {
        assertEquals(4, WeightTrend.entries.size)
    }

    @Test
    fun `WeightTrend - 각 값을 valueOf로 조회할 수 있다`() {
        assertEquals(WeightTrend.UP, WeightTrend.valueOf("UP"))
        assertEquals(WeightTrend.DOWN, WeightTrend.valueOf("DOWN"))
        assertEquals(WeightTrend.FLAT, WeightTrend.valueOf("FLAT"))
        assertEquals(WeightTrend.NONE, WeightTrend.valueOf("NONE"))
    }

    // ==========================================================
    // AmountRankingItem with market/sector/weight 테스트
    // ==========================================================

    @Test
    fun `AmountRankingItem - market과 sector 기본값은 null이다`() {
        val item = AmountRankingItem(
            rank = 1, stockTicker = "005930", stockName = "삼성전자",
            totalAmountBillion = 100.0, etfCount = 10
        )
        assertNull(item.market)
        assertNull(item.sector)
        assertNull(item.maxWeight)
        assertEquals(WeightTrend.NONE, item.maxWeightTrend)
    }

    @Test
    fun `AmountRankingItem - market과 sector를 설정할 수 있다`() {
        val item = AmountRankingItem(
            rank = 1, stockTicker = "005930", stockName = "삼성전자",
            totalAmountBillion = 100.0, etfCount = 10,
            market = "KOSPI", sector = "전기전자",
            maxWeight = 7.5, maxWeightTrend = WeightTrend.UP
        )
        assertEquals("KOSPI", item.market)
        assertEquals("전기전자", item.sector)
        assertEquals(7.5, item.maxWeight!!, 1e-10)
        assertEquals(WeightTrend.UP, item.maxWeightTrend)
    }

    @Test
    fun `StockChange - market과 sector 기본값은 null이다`() {
        val change = StockChange(
            stockTicker = "005930", stockName = "삼성전자",
            etfTicker = "069500", etfName = "KODEX 200",
            previousWeight = null, currentWeight = 5.0,
            previousAmount = 0L, currentAmount = 1_000_000L,
            changeType = ChangeType.NEW
        )
        assertNull(change.market)
        assertNull(change.sector)
    }

    @Test
    fun `StockSearchResult - market과 sector를 설정할 수 있다`() {
        val result = StockSearchResult(
            stock_ticker = "005930", stock_name = "삼성전자",
            market = "KOSPI", sector = "전기전자"
        )
        assertEquals("KOSPI", result.market)
        assertEquals("전기전자", result.sector)
    }
}
