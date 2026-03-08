package com.tinyoscillator.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * 시장 지표 도메인 모델 단위 테스트
 *
 * MarketOscillator, MarketDeposit, MarketDepositChartData,
 * OscillatorRangeOption, DateRangeOption, MarketOscillatorState, MarketDepositState 검증
 */
class MarketIndicatorModelsTest {

    // ==========================================================
    // MarketOscillator.getStatusKorean() 테스트
    // ==========================================================

    @Test
    fun `getStatusKorean - oscillator가 80 이상이면 극도과매수를 반환한다`() {
        val model = createMarketOscillator(oscillator = 80.0)
        assertEquals("극도과매수", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 80 초과이면 극도과매수를 반환한다`() {
        val model = createMarketOscillator(oscillator = 95.0)
        assertEquals("극도과매수", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 100이면 극도과매수를 반환한다`() {
        val model = createMarketOscillator(oscillator = 100.0)
        assertEquals("극도과매수", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 50 이상 80 미만이면 과매수를 반환한다`() {
        val model = createMarketOscillator(oscillator = 50.0)
        assertEquals("과매수", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 79점9이면 과매수를 반환한다`() {
        val model = createMarketOscillator(oscillator = 79.9)
        assertEquals("과매수", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 마이너스80 이하이면 극도과매도를 반환한다`() {
        val model = createMarketOscillator(oscillator = -80.0)
        assertEquals("극도과매도", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 마이너스100이면 극도과매도를 반환한다`() {
        val model = createMarketOscillator(oscillator = -100.0)
        assertEquals("극도과매도", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 마이너스50 이하 마이너스80 초과이면 과매도를 반환한다`() {
        val model = createMarketOscillator(oscillator = -50.0)
        assertEquals("과매도", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 마이너스79점9이면 과매도를 반환한다`() {
        val model = createMarketOscillator(oscillator = -79.9)
        assertEquals("과매도", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 0이면 중립을 반환한다`() {
        val model = createMarketOscillator(oscillator = 0.0)
        assertEquals("중립", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 49점9이면 중립을 반환한다`() {
        val model = createMarketOscillator(oscillator = 49.9)
        assertEquals("중립", model.getStatusKorean())
    }

    @Test
    fun `getStatusKorean - oscillator가 마이너스49점9이면 중립을 반환한다`() {
        val model = createMarketOscillator(oscillator = -49.9)
        assertEquals("중립", model.getStatusKorean())
    }

    // ==========================================================
    // MarketOscillator data class 테스트
    // ==========================================================

    @Test
    fun `MarketOscillator 생성 및 프로퍼티 접근`() {
        val model = MarketOscillator(
            id = "kospi_20240101",
            market = "KOSPI",
            date = "20240101",
            indexValue = 2650.5,
            oscillator = 35.0,
            lastUpdated = 1704067200000L
        )
        assertEquals("kospi_20240101", model.id)
        assertEquals("KOSPI", model.market)
        assertEquals("20240101", model.date)
        assertEquals(2650.5, model.indexValue, 1e-10)
        assertEquals(35.0, model.oscillator, 1e-10)
        assertEquals(1704067200000L, model.lastUpdated)
    }

    @Test
    fun `MarketOscillator equals 및 hashCode`() {
        val m1 = createMarketOscillator(oscillator = 50.0)
        val m2 = createMarketOscillator(oscillator = 50.0)
        assertEquals(m1, m2)
        assertEquals(m1.hashCode(), m2.hashCode())
    }

    @Test
    fun `MarketOscillator copy`() {
        val original = createMarketOscillator(oscillator = 50.0)
        val copied = original.copy(oscillator = -50.0)
        assertEquals(-50.0, copied.oscillator, 1e-10)
        assertEquals(original.id, copied.id)
    }

    // ==========================================================
    // MarketDeposit 테스트
    // ==========================================================

    @Test
    fun `MarketDeposit 생성 및 프로퍼티 접근`() {
        val deposit = MarketDeposit(
            date = "20240101",
            depositAmount = 50000.0,
            depositChange = 1000.0,
            creditAmount = 20000.0,
            creditChange = -500.0,
            lastUpdated = 1704067200000L
        )
        assertEquals("20240101", deposit.date)
        assertEquals(50000.0, deposit.depositAmount, 1e-10)
        assertEquals(1000.0, deposit.depositChange, 1e-10)
        assertEquals(20000.0, deposit.creditAmount, 1e-10)
        assertEquals(-500.0, deposit.creditChange, 1e-10)
    }

    // ==========================================================
    // MarketDepositChartData 테스트
    // ==========================================================

    @Test
    fun `MarketDepositChartData empty는 모든 리스트가 비어있다`() {
        val empty = MarketDepositChartData.empty()
        assertTrue(empty.dates.isEmpty())
        assertTrue(empty.depositAmounts.isEmpty())
        assertTrue(empty.depositChanges.isEmpty())
        assertTrue(empty.creditAmounts.isEmpty())
        assertTrue(empty.creditChanges.isEmpty())
    }

    @Test
    fun `MarketDepositChartData 생성 및 프로퍼티 접근`() {
        val data = MarketDepositChartData(
            dates = listOf("20240101", "20240102"),
            depositAmounts = listOf(50000.0, 51000.0),
            depositChanges = listOf(0.0, 1000.0),
            creditAmounts = listOf(20000.0, 19500.0),
            creditChanges = listOf(0.0, -500.0)
        )
        assertEquals(2, data.dates.size)
        assertEquals(51000.0, data.depositAmounts[1], 1e-10)
    }

    // ==========================================================
    // OscillatorRangeOption 테스트
    // ==========================================================

    @Test
    fun `OscillatorRangeOption - 각 옵션의 days 값이 올바르다`() {
        assertEquals(7, OscillatorRangeOption.ONE_WEEK.days)
        assertEquals(14, OscillatorRangeOption.TWO_WEEKS.days)
        assertEquals(21, OscillatorRangeOption.THREE_WEEKS.days)
        assertEquals(28, OscillatorRangeOption.FOUR_WEEKS.days)
    }

    @Test
    fun `OscillatorRangeOption - 각 옵션의 label이 올바르다`() {
        assertEquals("1주", OscillatorRangeOption.ONE_WEEK.label)
        assertEquals("2주", OscillatorRangeOption.TWO_WEEKS.label)
        assertEquals("3주", OscillatorRangeOption.THREE_WEEKS.label)
        assertEquals("4주", OscillatorRangeOption.FOUR_WEEKS.label)
    }

    @Test
    fun `OscillatorRangeOption - DEFAULT는 FOUR_WEEKS이다`() {
        assertEquals(OscillatorRangeOption.FOUR_WEEKS, OscillatorRangeOption.DEFAULT)
    }

    @Test
    fun `OscillatorRangeOption - ONE_WEEK 날짜 범위 계산`() {
        val (start, end) = OscillatorRangeOption.calculateDateRange(OscillatorRangeOption.ONE_WEEK)
        val expectedEnd = LocalDate.now().toString()
        val expectedStart = LocalDate.now().minusDays(7).toString()
        assertEquals(expectedStart, start)
        assertEquals(expectedEnd, end)
    }

    @Test
    fun `OscillatorRangeOption - TWO_WEEKS 날짜 범위 계산`() {
        val (start, end) = OscillatorRangeOption.calculateDateRange(OscillatorRangeOption.TWO_WEEKS)
        val expectedStart = LocalDate.now().minusDays(14).toString()
        assertEquals(expectedStart, start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `OscillatorRangeOption - THREE_WEEKS 날짜 범위 계산`() {
        val (start, end) = OscillatorRangeOption.calculateDateRange(OscillatorRangeOption.THREE_WEEKS)
        val expectedStart = LocalDate.now().minusDays(21).toString()
        assertEquals(expectedStart, start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `OscillatorRangeOption - FOUR_WEEKS 날짜 범위 계산`() {
        val (start, end) = OscillatorRangeOption.calculateDateRange(OscillatorRangeOption.FOUR_WEEKS)
        val expectedStart = LocalDate.now().minusDays(28).toString()
        assertEquals(expectedStart, start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `OscillatorRangeOption - entries 개수가 4개이다`() {
        assertEquals(4, OscillatorRangeOption.entries.size)
    }

    // ==========================================================
    // DateRangeOption 테스트
    // ==========================================================

    @Test
    fun `DateRangeOption - 각 옵션의 days 값이 올바르다`() {
        assertEquals(7, DateRangeOption.WEEK.days)
        assertEquals(30, DateRangeOption.MONTH.days)
        assertEquals(90, DateRangeOption.THREE_MONTHS.days)
        assertEquals(180, DateRangeOption.SIX_MONTHS.days)
        assertEquals(365, DateRangeOption.YEAR.days)
        assertEquals(-1, DateRangeOption.ALL.days)
    }

    @Test
    fun `DateRangeOption - 각 옵션의 label이 올바르다`() {
        assertEquals("1주", DateRangeOption.WEEK.label)
        assertEquals("1개월", DateRangeOption.MONTH.label)
        assertEquals("3개월", DateRangeOption.THREE_MONTHS.label)
        assertEquals("6개월", DateRangeOption.SIX_MONTHS.label)
        assertEquals("1년", DateRangeOption.YEAR.label)
        assertEquals("전체", DateRangeOption.ALL.label)
    }

    @Test
    fun `DateRangeOption - DEFAULT는 YEAR이다`() {
        assertEquals(DateRangeOption.YEAR, DateRangeOption.DEFAULT)
    }

    @Test
    fun `DateRangeOption - WEEK 날짜 범위 계산`() {
        val (start, end) = DateRangeOption.calculateDateRange(DateRangeOption.WEEK)
        assertEquals(LocalDate.now().minusDays(7).toString(), start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `DateRangeOption - MONTH 날짜 범위 계산`() {
        val (start, end) = DateRangeOption.calculateDateRange(DateRangeOption.MONTH)
        assertEquals(LocalDate.now().minusDays(30).toString(), start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `DateRangeOption - THREE_MONTHS 날짜 범위 계산`() {
        val (start, end) = DateRangeOption.calculateDateRange(DateRangeOption.THREE_MONTHS)
        assertEquals(LocalDate.now().minusDays(90).toString(), start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `DateRangeOption - SIX_MONTHS 날짜 범위 계산`() {
        val (start, end) = DateRangeOption.calculateDateRange(DateRangeOption.SIX_MONTHS)
        assertEquals(LocalDate.now().minusDays(180).toString(), start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `DateRangeOption - YEAR 날짜 범위 계산`() {
        val (start, end) = DateRangeOption.calculateDateRange(DateRangeOption.YEAR)
        assertEquals(LocalDate.now().minusDays(365).toString(), start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `DateRangeOption - ALL은 시작일이 2000-01-01이다`() {
        val (start, end) = DateRangeOption.calculateDateRange(DateRangeOption.ALL)
        assertEquals("2000-01-01", start)
        assertEquals(LocalDate.now().toString(), end)
    }

    @Test
    fun `DateRangeOption - entries 개수가 6개이다`() {
        assertEquals(6, DateRangeOption.entries.size)
    }

    // ==========================================================
    // MarketOscillatorState sealed class 테스트
    // ==========================================================

    @Test
    fun `MarketOscillatorState - Loading 인스턴스 확인`() {
        val state: MarketOscillatorState = MarketOscillatorState.Loading
        assertTrue(state is MarketOscillatorState.Loading)
    }

    @Test
    fun `MarketOscillatorState - Idle 상태 생성`() {
        val state = MarketOscillatorState.Idle(hasData = true, latestDate = "20240101")
        assertTrue(state.hasData)
        assertEquals("20240101", state.latestDate)
    }

    @Test
    fun `MarketOscillatorState - Idle 데이터 없음`() {
        val state = MarketOscillatorState.Idle(hasData = false, latestDate = null)
        assertFalse(state.hasData)
        assertNull(state.latestDate)
    }

    @Test
    fun `MarketOscillatorState - Initializing 상태`() {
        val state = MarketOscillatorState.Initializing(message = "초기화 중...", progress = 50)
        assertEquals("초기화 중...", state.message)
        assertEquals(50, state.progress)
    }

    @Test
    fun `MarketOscillatorState - Updating 상태`() {
        val state = MarketOscillatorState.Updating(message = "업데이트 중...")
        assertEquals("업데이트 중...", state.message)
    }

    @Test
    fun `MarketOscillatorState - Success 상태`() {
        val state = MarketOscillatorState.Success(message = "완료")
        assertEquals("완료", state.message)
    }

    @Test
    fun `MarketOscillatorState - Error 상태`() {
        val state = MarketOscillatorState.Error(message = "오류 발생")
        assertEquals("오류 발생", state.message)
    }

    // ==========================================================
    // MarketDepositState sealed class 테스트
    // ==========================================================

    @Test
    fun `MarketDepositState - Idle 인스턴스 확인`() {
        val state: MarketDepositState = MarketDepositState.Idle
        assertTrue(state is MarketDepositState.Idle)
    }

    @Test
    fun `MarketDepositState - Loading 인스턴스 확인`() {
        val state: MarketDepositState = MarketDepositState.Loading()
        assertTrue(state is MarketDepositState.Loading)
    }

    @Test
    fun `MarketDepositState - Success 상태`() {
        val state = MarketDepositState.Success(message = "자금 동향 로드 완료")
        assertEquals("자금 동향 로드 완료", state.message)
    }

    @Test
    fun `MarketDepositState - Error 상태`() {
        val state = MarketDepositState.Error(message = "데이터 로드 실패")
        assertEquals("데이터 로드 실패", state.message)
    }

    // ==========================================================
    // 헬퍼 함수
    // ==========================================================

    private fun createMarketOscillator(oscillator: Double) = MarketOscillator(
        id = "test_20240101",
        market = "KOSPI",
        date = "20240101",
        indexValue = 2650.0,
        oscillator = oscillator,
        lastUpdated = 1704067200000L
    )
}
