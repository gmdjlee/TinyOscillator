package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkPeriodType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * DeMark TD Sequential 계산 검증 테스트
 */
class CalcDemarkTDUseCaseTest {

    private lateinit var useCase: CalcDemarkTDUseCase

    @Before
    fun setup() {
        useCase = CalcDemarkTDUseCase()
    }

    // -- 헬퍼 --

    private fun daily(date: String, close: Int, marketCap: Long = 100_000_000_000_000L) =
        DailyTrading(
            date = date,
            marketCap = marketCap,
            foreignNetBuy = 0,
            instNetBuy = 0,
            closePrice = close
        )

    // ==========================================================
    // 빈 데이터 / 최소 데이터 테스트
    // ==========================================================

    @Test(expected = IllegalArgumentException::class)
    fun `빈 데이터 입력 시 예외`() {
        useCase.execute(emptyList(), DemarkPeriodType.DAILY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `5개 미만 데이터 시 예외`() {
        val data = listOf(
            daily("20240101", 100),
            daily("20240102", 110),
            daily("20240103", 120),
            daily("20240104", 130)
        )
        useCase.execute(data, DemarkPeriodType.DAILY)
    }

    @Test
    fun `정확히 5개 데이터 시 계산 성공`() {
        val data = listOf(
            daily("20240101", 100),
            daily("20240102", 110),
            daily("20240103", 120),
            daily("20240104", 130),
            daily("20240105", 140)
        )
        val result = useCase.execute(data, DemarkPeriodType.DAILY)
        assertEquals(5, result.size)
    }

    // ==========================================================
    // t-4 비교 이전 행 (인덱스 0~3)
    // ==========================================================

    @Test
    fun `처음 4일은 tdSell과 tdBuy가 모두 0이다`() {
        val data = listOf(
            daily("20240101", 100),
            daily("20240102", 110),
            daily("20240103", 120),
            daily("20240104", 130),
            daily("20240105", 140)
        )
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        for (i in 0..3) {
            assertEquals("Day $i tdSell", 0, result[i].tdSellCount)
            assertEquals("Day $i tdBuy", 0, result[i].tdBuyCount)
        }
    }

    // ==========================================================
    // 연속 상승 → TD Sell 카운트
    // ==========================================================

    @Test
    fun `연속 상승 5일 시 tdSell이 1이다`() {
        // 5일간 매일 상승: Close[4] > Close[0]
        val data = listOf(
            daily("20240101", 100),
            daily("20240102", 110),
            daily("20240103", 120),
            daily("20240104", 130),
            daily("20240105", 140) // 140 > 100 → sell=1
        )
        val result = useCase.execute(data, DemarkPeriodType.DAILY)
        assertEquals(1, result[4].tdSellCount)
        assertEquals(0, result[4].tdBuyCount)
    }

    @Test
    fun `연속 상승 13일 시 tdSell이 9에 도달한다`() {
        // 13일 연속 상승 데이터
        val data = (0 until 13).map { i ->
            daily(String.format("2024%02d%02d", 1, i + 1), 100 + i * 10)
        }
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        // 인덱스 4에서 첫 비교 시작: close[4]=140 > close[0]=100 → sell=1
        // 인덱스 5: close[5]=150 > close[1]=110 → sell=2
        // ...
        // 인덱스 12: close[12]=220 > close[8]=180 → sell=9
        assertEquals("인덱스4 tdSell", 1, result[4].tdSellCount)
        assertEquals("인덱스5 tdSell", 2, result[5].tdSellCount)
        assertEquals("인덱스12 tdSell=9", 9, result[12].tdSellCount)
    }

    // ==========================================================
    // 연속 하락 → TD Buy 카운트
    // ==========================================================

    @Test
    fun `연속 하락 시 tdBuy 카운트가 증가한다`() {
        val data = (0 until 10).map { i ->
            daily(String.format("2024%02d%02d", 1, i + 1), 200 - i * 10)
        }
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        // 인덱스 4: close[4]=160 < close[0]=200 → buy=1
        assertEquals(1, result[4].tdBuyCount)
        assertEquals(0, result[4].tdSellCount)
        // 인덱스 5: close[5]=150 < close[1]=190 → buy=2
        assertEquals(2, result[5].tdBuyCount)
    }

    // ==========================================================
    // 동일 종가 → 리셋
    // ==========================================================

    @Test
    fun `동일 종가 시 양쪽 카운트가 리셋된다`() {
        val data = listOf(
            daily("20240101", 100),
            daily("20240102", 100),
            daily("20240103", 100),
            daily("20240104", 100),
            daily("20240105", 110), // 110 > 100 → sell=1
            daily("20240106", 100)  // 100 == 100 (vs [2]) → reset
        )
        val result = useCase.execute(data, DemarkPeriodType.DAILY)
        assertEquals("sell=1 at day5", 1, result[4].tdSellCount)
        assertEquals("reset at day6 sell", 0, result[5].tdSellCount)
        assertEquals("reset at day6 buy", 0, result[5].tdBuyCount)
    }

    // ==========================================================
    // 추세 전환 → 반대쪽 리셋
    // ==========================================================

    @Test
    fun `상승에서 하락 전환 시 sell이 리셋되고 buy가 시작된다`() {
        val data = listOf(
            daily("20240101", 100),
            daily("20240102", 110),
            daily("20240103", 120),
            daily("20240104", 130),
            daily("20240105", 140), // 140 > 100 → sell=1
            daily("20240106", 150), // 150 > 110 → sell=2
            daily("20240107", 100), // 100 < 120 → buy=1, sell=0
            daily("20240108", 90),  // 90 < 130 → buy=2
        )
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        assertEquals("sell=1", 1, result[4].tdSellCount)
        assertEquals("sell=2", 2, result[5].tdSellCount)
        assertEquals("buy=1 after reversal", 1, result[6].tdBuyCount)
        assertEquals("sell=0 after reversal", 0, result[6].tdSellCount)
        assertEquals("buy=2", 2, result[7].tdBuyCount)
    }

    // ==========================================================
    // 시가총액 변환 검증
    // ==========================================================

    @Test
    fun `시가총액 조단위 변환이 정확하다`() {
        val data = (0 until 5).map { i ->
            daily(
                String.format("2024%02d%02d", 1, i + 1),
                100 + i * 10,
                4_500_000_000_000_000L
            )
        }
        val result = useCase.execute(data, DemarkPeriodType.DAILY)
        assertEquals(4500.0, result[0].marketCapTril, 0.001)
    }

    // ==========================================================
    // 주봉 리샘플링 테스트
    // ==========================================================

    @Test
    fun `주봉 리샘플링이 주간 마지막 거래일을 사용한다`() {
        // 2024-01-08(월) ~ 2024-01-12(금) = ISO week 2
        // 2024-01-15(월) ~ 2024-01-19(금) = ISO week 3
        val data = listOf(
            daily("20240108", 100), // Mon wk2
            daily("20240109", 110), // Tue wk2
            daily("20240110", 120), // Wed wk2
            daily("20240111", 130), // Thu wk2
            daily("20240112", 140), // Fri wk2 ← 주봉으로 선택
            daily("20240115", 150), // Mon wk3
            daily("20240116", 160), // Tue wk3
            daily("20240117", 170), // Wed wk3
            daily("20240118", 180), // Thu wk3
            daily("20240119", 190)  // Fri wk3 ← 주봉으로 선택
        )

        val weekly = useCase.resampleToWeekly(data)
        assertEquals(2, weekly.size)
        assertEquals("20240112", weekly[0].date)
        assertEquals(140, weekly[0].closePrice)
        assertEquals("20240119", weekly[1].date)
        assertEquals(190, weekly[1].closePrice)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `주봉 변환 후 5개 미만이면 예외`() {
        // 2주 데이터만 = 주봉 2개 → 5개 미만
        val data = listOf(
            daily("20240108", 100),
            daily("20240112", 140),
            daily("20240115", 150),
            daily("20240119", 190)
        )
        useCase.execute(data, DemarkPeriodType.WEEKLY)
    }

    @Test
    fun `주봉 모드에서 TD 카운트가 정확하다`() {
        // 6주간 연속 상승 데이터 (주당 5일)
        val data = mutableListOf<DailyTrading>()
        var basePrice = 1000
        // Generate 6 weeks × 5 days of daily data (Mon-Fri)
        // Start from 2024-01-08 (Monday of ISO week 2)
        val startDates = listOf(
            // Week 2: Jan 8-12
            listOf("20240108", "20240109", "20240110", "20240111", "20240112"),
            // Week 3: Jan 15-19
            listOf("20240115", "20240116", "20240117", "20240118", "20240119"),
            // Week 4: Jan 22-26
            listOf("20240122", "20240123", "20240124", "20240125", "20240126"),
            // Week 5: Jan 29 - Feb 2
            listOf("20240129", "20240130", "20240131", "20240201", "20240202"),
            // Week 6: Feb 5-9
            listOf("20240205", "20240206", "20240207", "20240208", "20240209"),
            // Week 7: Feb 12-16
            listOf("20240212", "20240213", "20240214", "20240215", "20240216"),
        )

        for (week in startDates) {
            for (date in week) {
                data.add(daily(date, basePrice))
                basePrice += 10
            }
        }

        val result = useCase.execute(data, DemarkPeriodType.WEEKLY)

        // 주봉 6개: 처음 4개는 tdSell=0, 5번째(인덱스4)부터 카운트
        assertEquals(6, result.size)
        assertEquals(0, result[0].tdSellCount)
        assertEquals(0, result[3].tdSellCount)
        // 인덱스4: 주봉[4].close > 주봉[0].close → sell=1
        assertEquals(1, result[4].tdSellCount)
        assertEquals(2, result[5].tdSellCount)
    }

    // ==========================================================
    // closePrice와 date 정확성
    // ==========================================================

    @Test
    fun `결과 행의 closePrice와 date가 원본과 일치한다`() {
        val data = (0 until 5).map { i ->
            daily(String.format("2024%02d%02d", 1, i + 1), 100 + i * 5)
        }
        val result = useCase.execute(data, DemarkPeriodType.DAILY)

        for (i in data.indices) {
            assertEquals(data[i].date, result[i].date)
            assertEquals(data[i].closePrice, result[i].closePrice)
        }
    }
}
