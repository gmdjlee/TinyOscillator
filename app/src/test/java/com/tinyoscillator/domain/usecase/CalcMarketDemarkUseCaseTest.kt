package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.DemarkPeriodType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * 시장 지수 DeMark TD Sequential 계산 검증 테스트
 */
class CalcMarketDemarkUseCaseTest {

    private lateinit var useCase: CalcMarketDemarkUseCase

    @Before
    fun setup() {
        useCase = CalcMarketDemarkUseCase()
    }

    // -- 헬퍼 --

    private fun idx(date: String, close: Double) =
        CalcMarketDemarkUseCase.IndexDay(date, close)

    // ==========================================================
    // 빈 데이터 / 최소 데이터 테스트
    // ==========================================================

    @Test(expected = IllegalArgumentException::class)
    fun `빈 데이터 예외`() {
        useCase.execute(emptyList(), DemarkPeriodType.DAILY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `5개 미만 예외`() {
        val data = listOf(
            idx("20240101", 2500.0),
            idx("20240102", 2510.0),
            idx("20240103", 2520.0),
            idx("20240104", 2530.0)
        )
        useCase.execute(data, DemarkPeriodType.DAILY)
    }

    // ==========================================================
    // 정확히 5개 데이터
    // ==========================================================

    @Test
    fun `정확히 5개 데이터 — 첫 4개 카운트 0`() {
        val data = listOf(
            idx("20240101", 2500.0),
            idx("20240102", 2510.0),
            idx("20240103", 2520.0),
            idx("20240104", 2530.0),
            idx("20240105", 2540.0)
        )
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        assertEquals(5, result.size)
        for (i in 0..3) {
            assertEquals("Day $i tdSell", 0, result[i].tdSellCount)
            assertEquals("Day $i tdBuy", 0, result[i].tdBuyCount)
        }
    }

    // ==========================================================
    // 연속 상승 → TD Sell 카운트
    // ==========================================================

    @Test
    fun `연속 상승 — tdSell 누적`() {
        // 13일 연속 상승
        val data = (0 until 13).map { i ->
            idx(String.format("2024%02d%02d", 1, i + 1), 2500.0 + i * 10.0)
        }
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        // 처음 4개: tdSell=0
        for (i in 0..3) {
            assertEquals(0, result[i].tdSellCount)
        }
        // index 4: close[4]=2540 > close[0]=2500 → sell=1
        assertEquals(1, result[4].tdSellCount)
        assertEquals(0, result[4].tdBuyCount)
        // index 5: close[5]=2550 > close[1]=2510 → sell=2
        assertEquals(2, result[5].tdSellCount)
        // index 12: sell=9
        assertEquals(9, result[12].tdSellCount)
    }

    // ==========================================================
    // 연속 하락 → TD Buy 카운트
    // ==========================================================

    @Test
    fun `연속 하락 — tdBuy 누적`() {
        // 10일 연속 하락
        val data = (0 until 10).map { i ->
            idx(String.format("2024%02d%02d", 1, i + 1), 2700.0 - i * 10.0)
        }
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        // index 4: close[4]=2660 < close[0]=2700 → buy=1
        assertEquals(1, result[4].tdBuyCount)
        assertEquals(0, result[4].tdSellCount)
        // index 5: close[5]=2650 < close[1]=2690 → buy=2
        assertEquals(2, result[5].tdBuyCount)
        // index 9: buy=6
        assertEquals(6, result[9].tdBuyCount)
    }

    // ==========================================================
    // 동일 종가 → 리셋
    // ==========================================================

    @Test
    fun `동일 종가 — 리셋`() {
        val data = listOf(
            idx("20240101", 2500.0),
            idx("20240102", 2500.0),
            idx("20240103", 2500.0),
            idx("20240104", 2500.0),
            idx("20240105", 2550.0),  // 2550 > 2500 → sell=1
            idx("20240106", 2500.0)   // 2500 == 2500 (vs [2]) → reset
        )
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        assertEquals("sell=1 at day5", 1, result[4].tdSellCount)
        assertEquals("reset sell at day6", 0, result[5].tdSellCount)
        assertEquals("reset buy at day6", 0, result[5].tdBuyCount)
    }

    // ==========================================================
    // 주봉 리샘플링 테스트
    // ==========================================================

    @Test
    fun `주봉 리샘플링 — 주간 마지막 거래일 사용`() {
        // 2024-01-08(월) ~ 2024-01-12(금) = ISO week 2
        // 2024-01-15(월) ~ 2024-01-19(금) = ISO week 3
        val data = listOf(
            idx("20240108", 2500.0),  // Mon wk2
            idx("20240109", 2510.0),  // Tue wk2
            idx("20240110", 2520.0),  // Wed wk2
            idx("20240111", 2530.0),  // Thu wk2
            idx("20240112", 2540.0),  // Fri wk2 ← 주봉으로 선택
            idx("20240115", 2550.0),  // Mon wk3
            idx("20240116", 2560.0),  // Tue wk3
            idx("20240117", 2570.0),  // Wed wk3
            idx("20240118", 2580.0),  // Thu wk3
            idx("20240119", 2590.0)   // Fri wk3 ← 주봉으로 선택
        )

        val weekly = useCase.resampleToWeekly(data)
        assertEquals(2, weekly.size)
        assertEquals("20240112", weekly[0].date)
        assertEquals(2540.0, weekly[0].close, 0.001)
        assertEquals("20240119", weekly[1].date)
        assertEquals(2590.0, weekly[1].close, 0.001)
    }
}
