package com.tinyoscillator.domain.usecase

import com.krxkt.KrxIndex
import com.krxkt.KrxStock
import com.krxkt.model.IndexOhlcv
import com.krxkt.model.Market
import com.krxkt.model.MarketOhlcv
import com.tinyoscillator.core.api.KrxApiClient
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * MarketOscillatorCalculator 단위 테스트
 *
 * 시장 과매수/과매도 지표 계산 로직 검증
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketOscillatorCalculatorTest {

    private lateinit var krxApiClient: KrxApiClient
    private lateinit var calculator: MarketOscillatorCalculator

    private val krxIndex: KrxIndex = mockk()
    private val krxStock: KrxStock = mockk()

    @Before
    fun setup() {
        krxApiClient = mockk()
        calculator = MarketOscillatorCalculator(krxApiClient)
    }

    private fun createIndexOhlcv(date: String, close: Double = 2500.0) = IndexOhlcv(
        date = date,
        open = close - 10.0,
        high = close + 20.0,
        low = close - 20.0,
        close = close,
        volume = 1_000_000L,
        tradingValue = 5_000_000_000L,
        changeType = 1,
        change = 10.0
    )

    private fun createMarketOhlcv(
        ticker: String,
        changeRate: Double,
        volume: Long = 100_000L
    ) = MarketOhlcv(
        ticker = ticker,
        name = "종목$ticker",
        open = 50000L,
        high = 52000L,
        low = 49000L,
        close = 51000L,
        volume = volume,
        tradingValue = 5_000_000_000L,
        changeRate = changeRate
    )

    // ── 1. 로그인 실패 시 null 반환 ──

    @Test
    fun `KRX 로그인 실패 시 null 반환 - krxIndex가 null`() = runTest {
        every { krxApiClient.getKrxIndex() } returns null
        every { krxApiClient.getKrxStock() } returns krxStock

        val result = calculator.analyze("KOSPI", "20260301", "20260308")

        assertNull("krxIndex null이면 null 반환", result)
    }

    @Test
    fun `KRX 로그인 실패 시 null 반환 - krxStock이 null`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns null

        val result = calculator.analyze("KOSPI", "20260301", "20260308")

        assertNull("krxStock null이면 null 반환", result)
    }

    @Test
    fun `KRX 로그인 실패 시 null 반환 - 둘 다 null`() = runTest {
        every { krxApiClient.getKrxIndex() } returns null
        every { krxApiClient.getKrxStock() } returns null

        val result = calculator.analyze("KOSPI", "20260301", "20260308")

        assertNull("둘 다 null이면 null 반환", result)
    }

    // ── 2. 인덱스 데이터가 비어있으면 null 반환 ──

    @Test
    fun `KOSPI 인덱스 데이터가 비어있으면 null 반환`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock
        coEvery { krxIndex.getKospi("20260301", "20260308") } returns emptyList()

        val result = calculator.analyze("KOSPI", "20260301", "20260308")

        assertNull("빈 인덱스 데이터 → null", result)
    }

    @Test
    fun `KOSDAQ 인덱스 데이터가 비어있으면 null 반환`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock
        coEvery { krxIndex.getKosdaq("20260301", "20260308") } returns emptyList()

        val result = calculator.analyze("KOSDAQ", "20260301", "20260308")

        assertNull("빈 KOSDAQ 인덱스 데이터 → null", result)
    }

    // ── 3. 비선형 변환: avg > 0.5 → oscillator = avg * 100 ──

    @Test
    fun `avg가 0_5 초과이면 oscillator는 양수 50 초과 100 이하`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock

        val indexData = listOf(createIndexOhlcv("20260305"))
        coEvery { krxIndex.getKospi("20260305", "20260305") } returns indexData
        coEvery { krxIndex.getIndexPortfolioTickers("20260305", KrxIndex.TICKER_KOSPI_200) } returns listOf("005930", "000660")

        // 상승 종목만: upVol=200000, downVol=0 → volRatio=1.0
        // gained=3.5, lost=0 → ptsRatio=1.0
        // avg = (1.0+1.0)/2 = 1.0 > 0.5 → oscillator = 1.0 * 100 = 100.0
        val ohlcvList = listOf(
            createMarketOhlcv("005930", changeRate = 2.0, volume = 100_000L),
            createMarketOhlcv("000660", changeRate = 1.5, volume = 100_000L),
            createMarketOhlcv("999999", changeRate = -1.0, volume = 50_000L)  // 비구성 종목
        )
        coEvery { krxStock.getMarketOhlcv("20260305", Market.KOSPI) } returns ohlcvList

        val result = calculator.analyze("KOSPI", "20260305", "20260305")

        assertNotNull("결과가 null이 아님", result)
        assertEquals("oscillator 값 개수", 1, result!!.oscillator.size)
        assertEquals("모두 상승 → oscillator = 100.0", 100.0, result.oscillator[0], 0.001)
    }

    @Test
    fun `avg가 0_5 초과인 혼합 케이스`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock

        val indexData = listOf(createIndexOhlcv("20260305"))
        coEvery { krxIndex.getKospi("20260305", "20260305") } returns indexData
        coEvery { krxIndex.getIndexPortfolioTickers("20260305", KrxIndex.TICKER_KOSPI_200) } returns listOf("005930", "000660", "035420")

        // 005930: +3.0%, vol=300000 (상승)
        // 000660: +1.0%, vol=100000 (상승)
        // 035420: -0.5%, vol=50000 (하락)
        // upVol=400000, downVol=50000 → volRatio=400000/450000=0.8889
        // gained=4.0, lost=0.5 → ptsRatio=4.0/4.5=0.8889
        // avg=(0.8889+0.8889)/2=0.8889 > 0.5 → oscillator=88.89
        val ohlcvList = listOf(
            createMarketOhlcv("005930", changeRate = 3.0, volume = 300_000L),
            createMarketOhlcv("000660", changeRate = 1.0, volume = 100_000L),
            createMarketOhlcv("035420", changeRate = -0.5, volume = 50_000L)
        )
        coEvery { krxStock.getMarketOhlcv("20260305", Market.KOSPI) } returns ohlcvList

        val result = calculator.analyze("KOSPI", "20260305", "20260305")

        assertNotNull(result)
        val expected = ((400000.0 / 450000.0) + (4.0 / 4.5)) / 2.0 * 100.0
        assertEquals("혼합 상승 우세 oscillator", expected, result!!.oscillator[0], 0.001)
        assertTrue("oscillator > 50", result.oscillator[0] > 50.0)
    }

    // ── 4. 비선형 변환: avg <= 0.5 → oscillator = (avg - 1) * 100 ──

    @Test
    fun `avg가 0_5 이하이면 oscillator는 음수 -100 이상 -50 이하`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock

        val indexData = listOf(createIndexOhlcv("20260305"))
        coEvery { krxIndex.getKospi("20260305", "20260305") } returns indexData
        coEvery { krxIndex.getIndexPortfolioTickers("20260305", KrxIndex.TICKER_KOSPI_200) } returns listOf("005930", "000660")

        // 하락 종목만: upVol=0, downVol=200000 → volRatio=0.0
        // gained=0, lost=5.0 → ptsRatio=0.0
        // avg = (0.0+0.0)/2 = 0.0 <= 0.5 → oscillator = (0.0-1.0)*100 = -100.0
        val ohlcvList = listOf(
            createMarketOhlcv("005930", changeRate = -3.0, volume = 100_000L),
            createMarketOhlcv("000660", changeRate = -2.0, volume = 100_000L)
        )
        coEvery { krxStock.getMarketOhlcv("20260305", Market.KOSPI) } returns ohlcvList

        val result = calculator.analyze("KOSPI", "20260305", "20260305")

        assertNotNull(result)
        assertEquals("모두 하락 → oscillator = -100.0", -100.0, result!!.oscillator[0], 0.001)
    }

    @Test
    fun `avg가 정확히 0_5이면 oscillator는 -50`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock

        val indexData = listOf(createIndexOhlcv("20260305"))
        coEvery { krxIndex.getKospi("20260305", "20260305") } returns indexData
        coEvery { krxIndex.getIndexPortfolioTickers("20260305", KrxIndex.TICKER_KOSPI_200) } returns listOf("005930", "000660")

        // 동일 비중: upVol=100000, downVol=100000 → volRatio=0.5
        // gained=2.0, lost=2.0 → ptsRatio=0.5
        // avg = (0.5+0.5)/2 = 0.5 <= 0.5 → oscillator = (0.5-1.0)*100 = -50.0
        val ohlcvList = listOf(
            createMarketOhlcv("005930", changeRate = 2.0, volume = 100_000L),
            createMarketOhlcv("000660", changeRate = -2.0, volume = 100_000L)
        )
        coEvery { krxStock.getMarketOhlcv("20260305", Market.KOSPI) } returns ohlcvList

        val result = calculator.analyze("KOSPI", "20260305", "20260305")

        assertNotNull(result)
        assertEquals("avg=0.5 → oscillator = -50.0", -50.0, result!!.oscillator[0], 0.001)
    }

    @Test
    fun `avg가 0_5 이하인 혼합 케이스`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock

        val indexData = listOf(createIndexOhlcv("20260305"))
        coEvery { krxIndex.getKospi("20260305", "20260305") } returns indexData
        coEvery { krxIndex.getIndexPortfolioTickers("20260305", KrxIndex.TICKER_KOSPI_200) } returns listOf("005930", "000660", "035420")

        // 005930: -3.0%, vol=300000 (하락)
        // 000660: -1.0%, vol=100000 (하락)
        // 035420: +0.5%, vol=50000 (상승)
        // upVol=50000, downVol=400000 → volRatio=50000/450000=0.1111
        // gained=0.5, lost=4.0 → ptsRatio=0.5/4.5=0.1111
        // avg=(0.1111+0.1111)/2=0.1111 <= 0.5 → oscillator=(0.1111-1.0)*100=-88.89
        val ohlcvList = listOf(
            createMarketOhlcv("005930", changeRate = -3.0, volume = 300_000L),
            createMarketOhlcv("000660", changeRate = -1.0, volume = 100_000L),
            createMarketOhlcv("035420", changeRate = 0.5, volume = 50_000L)
        )
        coEvery { krxStock.getMarketOhlcv("20260305", Market.KOSPI) } returns ohlcvList

        val result = calculator.analyze("KOSPI", "20260305", "20260305")

        assertNotNull(result)
        val expected = (((50000.0 / 450000.0) + (0.5 / 4.5)) / 2.0 - 1.0) * 100.0
        assertEquals("혼합 하락 우세 oscillator", expected, result!!.oscillator[0], 0.001)
        assertTrue("oscillator < -50", result.oscillator[0] < -50.0)
    }

    // ── 5. CancellationException 전파 ──

    @Test(expected = CancellationException::class)
    fun `CancellationException은 catch하지 않고 전파한다`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock
        coEvery { krxIndex.getKospi(any(), any()) } throws CancellationException("Job cancelled")

        calculator.analyze("KOSPI", "20260301", "20260308")
    }

    @Test(expected = CancellationException::class)
    fun `getMarketOhlcv에서 CancellationException은 전파한다`() = runTest {
        every { krxApiClient.getKrxIndex() } returns krxIndex
        every { krxApiClient.getKrxStock() } returns krxStock

        val indexData = listOf(createIndexOhlcv("20260305"))
        coEvery { krxIndex.getKospi("20260305", "20260305") } returns indexData
        coEvery { krxIndex.getIndexPortfolioTickers("20260305", KrxIndex.TICKER_KOSPI_200) } returns listOf("005930")
        coEvery { krxStock.getMarketOhlcv("20260305", Market.KOSPI) } throws CancellationException("Cancelled")

        calculator.analyze("KOSPI", "20260305", "20260305")
    }
}
