package com.tinyoscillator.domain.model

import org.junit.Assert.*
import org.junit.Test

class PortfolioModelsTest {

    @Test
    fun `PortfolioSummary 생성 및 프로퍼티`() {
        val summary = PortfolioSummary(
            totalEvaluation = 10_000_000,
            totalInvested = 8_000_000,
            totalProfitLoss = 2_000_000,
            totalProfitLossPercent = 25.0,
            totalRealizedProfitLoss = 0,
            holdingsCount = 3
        )
        assertEquals(10_000_000L, summary.totalEvaluation)
        assertEquals(8_000_000L, summary.totalInvested)
        assertEquals(2_000_000L, summary.totalProfitLoss)
        assertEquals(25.0, summary.totalProfitLossPercent, 0.001)
        assertEquals(3, summary.holdingsCount)
    }

    @Test
    fun `PortfolioSummary 손실 케이스`() {
        val summary = PortfolioSummary(
            totalEvaluation = 7_000_000,
            totalInvested = 10_000_000,
            totalProfitLoss = -3_000_000,
            totalProfitLossPercent = -30.0,
            totalRealizedProfitLoss = 0,
            holdingsCount = 2
        )
        assertTrue(summary.totalProfitLoss < 0)
        assertTrue(summary.totalProfitLossPercent < 0)
    }

    @Test
    fun `PortfolioSummary 빈 포트폴리오`() {
        val summary = PortfolioSummary(
            totalEvaluation = 0,
            totalInvested = 0,
            totalProfitLoss = 0,
            totalProfitLossPercent = 0.0,
            totalRealizedProfitLoss = 0,
            holdingsCount = 0
        )
        assertEquals(0L, summary.totalEvaluation)
        assertEquals(0, summary.holdingsCount)
    }

    @Test
    fun `PortfolioHoldingItem 비중 계산 검증`() {
        val item = PortfolioHoldingItem(
            holdingId = 1,
            ticker = "005930",
            stockName = "삼성전자",
            market = "KOSPI",
            sector = "반도체",
            totalShares = 100,
            avgBuyPrice = 70000,
            currentPrice = 75000,
            targetPrice = 85000,
            weightPercent = 50.0,
            isOverWeight = true,
            rebalanceShares = 27,
            rebalanceAmount = 2_025_000,
            profitLossPercent = 7.14,
            profitLossAmount = 500_000,
            realizedProfitLoss = 0
        )
        assertEquals("005930", item.ticker)
        assertEquals(100, item.totalShares)
        assertEquals(70000, item.avgBuyPrice)
        assertEquals(75000L, item.currentPrice)
        assertEquals(50.0, item.weightPercent, 0.001)
        assertTrue(item.isOverWeight)
        assertEquals(27, item.rebalanceShares)
        assertTrue(item.profitLossPercent > 0)
        assertTrue(item.profitLossAmount > 0)
    }

    @Test
    fun `PortfolioHoldingItem 손실 종목`() {
        val item = PortfolioHoldingItem(
            holdingId = 2,
            ticker = "000660",
            stockName = "SK하이닉스",
            market = "KOSPI",
            sector = "반도체",
            totalShares = 50,
            avgBuyPrice = 150000,
            currentPrice = 120000,
            targetPrice = 0,
            weightPercent = 20.0,
            isOverWeight = false,
            rebalanceShares = 0,
            rebalanceAmount = 0,
            profitLossPercent = -20.0,
            profitLossAmount = -1_500_000,
            realizedProfitLoss = 0
        )
        assertFalse(item.isOverWeight)
        assertEquals(0, item.rebalanceShares)
        assertTrue(item.profitLossPercent < 0)
        assertTrue(item.profitLossAmount < 0)
    }

    @Test
    fun `PortfolioHoldingItem 현재가 0 - 미조회`() {
        val item = PortfolioHoldingItem(
            holdingId = 3,
            ticker = "035420",
            stockName = "NAVER",
            market = "KOSPI",
            sector = "IT",
            totalShares = 10,
            avgBuyPrice = 200000,
            currentPrice = 0,
            targetPrice = 0,
            weightPercent = 0.0,
            isOverWeight = false,
            rebalanceShares = 0,
            rebalanceAmount = 0,
            profitLossPercent = 0.0,
            profitLossAmount = 0,
            realizedProfitLoss = 0
        )
        assertEquals(0L, item.currentPrice)
        assertEquals(0.0, item.weightPercent, 0.001)
    }

    @Test
    fun `TransactionItem 매수 거래`() {
        val tx = TransactionItem(
            id = 1,
            date = "20260314",
            shares = 100,
            pricePerShare = 70000,
            memo = "초기 매수",
            currentPrice = 75000,
            profitLossPercent = 7.14,
            profitLossAmount = 500_000,
            realizedProfitLoss = 0
        )
        assertTrue(tx.shares > 0)
        assertTrue(tx.profitLossAmount > 0)
    }

    @Test
    fun `TransactionItem 매도 거래`() {
        val tx = TransactionItem(
            id = 2,
            date = "20260314",
            shares = -50,
            pricePerShare = 80000,
            memo = "일부 매도",
            currentPrice = 75000,
            profitLossPercent = -6.25,
            profitLossAmount = -250_000,
            realizedProfitLoss = 0
        )
        assertTrue(tx.shares < 0)
    }

    @Test
    fun `PortfolioUiState sealed class 타입 검증`() {
        val idle: PortfolioUiState = PortfolioUiState.Idle
        assertTrue(idle is PortfolioUiState.Idle)

        val loading: PortfolioUiState = PortfolioUiState.Loading("로딩 중...")
        assertTrue(loading is PortfolioUiState.Loading)
        assertEquals("로딩 중...", (loading as PortfolioUiState.Loading).message)

        val error: PortfolioUiState = PortfolioUiState.Error("에러 발생")
        assertTrue(error is PortfolioUiState.Error)
        assertEquals("에러 발생", (error as PortfolioUiState.Error).message)

        val summary = PortfolioSummary(1000, 800, 200, 25.0, 0, 1)
        val success: PortfolioUiState = PortfolioUiState.Success(summary, emptyList())
        assertTrue(success is PortfolioUiState.Success)
        assertEquals(1, (success as PortfolioUiState.Success).summary.holdingsCount)
    }

    @Test
    fun `비중 초과 리밸런싱 계산 시나리오`() {
        // 포트폴리오 총 1천만원, 최대비중 30%
        // 종목A: 현재가 100, 50주 = 5백만원 (50%)
        // 목표: 30% = 3백만원
        // 초과: 2백만원 → 매도 필요: 2백만 / 100 = 20주
        val item = PortfolioHoldingItem(
            holdingId = 1,
            ticker = "TEST",
            stockName = "테스트",
            market = "KOSPI",
            sector = "",
            totalShares = 50,
            avgBuyPrice = 80,
            currentPrice = 100,
            targetPrice = 0,
            weightPercent = 50.0,
            isOverWeight = true,
            rebalanceShares = 20,
            rebalanceAmount = 2000,
            profitLossPercent = 25.0,
            profitLossAmount = 1000,
            realizedProfitLoss = 0
        )
        assertTrue(item.isOverWeight)
        assertEquals(20, item.rebalanceShares)
    }
}
