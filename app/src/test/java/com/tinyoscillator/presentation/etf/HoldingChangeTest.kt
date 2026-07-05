package com.tinyoscillator.presentation.etf

import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldingChangeTest {

    private fun holding(
        stockTicker: String,
        weight: Double?,
        date: String = "20260705"
    ) = EtfHoldingEntity(
        etfTicker = "069500",
        stockTicker = stockTicker,
        date = date,
        stockName = "종목$stockTicker",
        weight = weight,
        shares = 100,
        amount = 1_000_000
    )

    @Test
    fun `직전 스냅샷에 없는 종목은 NEW`() {
        val current = listOf(holding("005930", 10.0), holding("000660", 5.0))
        val previous = listOf(holding("005930", 10.0, date = "20260704"))

        val changes = computeHoldingChanges(current, previous)

        assertEquals(HoldingChange.NEW, changes["000660"])
        assertNull(changes["005930"]) // 비중 동일 → 배지 없음
    }

    @Test
    fun `비중 증가는 INCREASED, 감소는 DECREASED`() {
        val current = listOf(holding("005930", 12.5), holding("000660", 3.2))
        val previous = listOf(
            holding("005930", 10.0, date = "20260704"),
            holding("000660", 5.0, date = "20260704")
        )

        val changes = computeHoldingChanges(current, previous)

        assertEquals(HoldingChange.INCREASED, changes["005930"])
        assertEquals(HoldingChange.DECREASED, changes["000660"])
    }

    @Test
    fun `표시 정밀도(소수 2자리) 이하 차이는 변화 없음`() {
        // 12.3401 vs 12.3399 → 둘 다 화면에 12.34%로 표시되므로 배지 없음
        val current = listOf(holding("005930", 12.3401))
        val previous = listOf(holding("005930", 12.3399, date = "20260704"))

        val changes = computeHoldingChanges(current, previous)

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `어느 한쪽 비중이 null이면 배지 없음`() {
        val current = listOf(holding("005930", null), holding("000660", 5.0))
        val previous = listOf(
            holding("005930", 10.0, date = "20260704"),
            holding("000660", null, date = "20260704")
        )

        val changes = computeHoldingChanges(current, previous)

        assertTrue(changes.isEmpty())
    }

    @Test
    fun `비중이 null이어도 직전에 없던 종목은 NEW`() {
        val current = listOf(holding("000660", null))
        val previous = listOf(holding("005930", 10.0, date = "20260704"))

        val changes = computeHoldingChanges(current, previous)

        assertEquals(HoldingChange.NEW, changes["000660"])
    }

    @Test
    fun `직전 스냅샷이 비어 있으면 전부 NEW`() {
        // ViewModel은 직전 날짜 자체가 없으면 계산을 건너뜀 —
        // 이 케이스는 직전 날짜는 있으나 해당 ETF 데이터가 빈 경우
        val current = listOf(holding("005930", 10.0))

        val changes = computeHoldingChanges(current, emptyList())

        assertEquals(HoldingChange.NEW, changes["005930"])
    }

    @Test
    fun `직전에만 있고 현재 없는 종목(편출)은 결과에 미포함`() {
        val current = listOf(holding("005930", 10.0))
        val previous = listOf(
            holding("005930", 10.0, date = "20260704"),
            holding("000660", 5.0, date = "20260704")
        )

        val changes = computeHoldingChanges(current, previous)

        assertNull(changes["000660"])
    }
}
