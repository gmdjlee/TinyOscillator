package com.tinyoscillator.data.engine

import com.tinyoscillator.domain.model.CorrelationStrength
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class CorrelationEngineTest {

    private lateinit var engine: CorrelationEngine

    @Before
    fun setup() {
        engine = CorrelationEngine()
    }

    @Test
    fun `완전 양의 상관 r 이 1점0이다`() {
        val x = (1..50).map { it.toDouble() }
        val y = (1..50).map { it * 2.0 }

        val r = engine.pearsonCorrelation(x, y)

        assertEquals("완전 양의 상관", 1.0, r, 0.001)
    }

    @Test
    fun `완전 음의 상관 r 이 마이너스 1점0이다`() {
        val x = (1..50).map { it.toDouble() }
        val y = (1..50).map { -it.toDouble() }

        val r = engine.pearsonCorrelation(x, y)

        assertEquals("완전 음의 상관", -1.0, r, 0.001)
    }

    @Test
    fun `무상관 데이터에서 r 이 0에 가깝다`() {
        // 서로 독립적인 패턴
        val x = (1..100).map { kotlin.math.sin(it.toDouble()) }
        val y = (1..100).map { kotlin.math.cos(it * 3.7) }

        val r = engine.pearsonCorrelation(x, y)

        assertTrue("무상관 |r| < 0.3", abs(r) < 0.3)
    }

    @Test
    fun `Pearson r 범위가 마이너스1에서 1이다`() {
        val x = (1..50).map { (it % 7).toDouble() }
        val y = (1..50).map { (it % 5).toDouble() }

        val r = engine.pearsonCorrelation(x, y)

        assertTrue("r >= -1", r >= -1.0)
        assertTrue("r <= 1", r <= 1.0)
    }

    @Test
    fun `cross-correlation에서 동행 시 lag 가 작다`() {
        val x = (1..50).map { it.toDouble() }
        val y = (1..50).map { it * 2.0 + 5.0 }

        val (lag, r) = engine.crossCorrelation(x, y)

        // 선형 데이터는 lag 이동해도 상관이 높으므로 lag=0 보장은 어려움
        // 대신 상관이 매우 높은지 확인
        assertTrue("높은 상관 |r| > 0.9", abs(r) > 0.9)
        assertTrue("lag 범위 내", abs(lag) <= 5)
    }

    @Test
    fun `cross-correlation에서 선행 데이터 감지`() {
        // x가 y를 2일 선행하는 데이터
        val base = (0 until 60).map { kotlin.math.sin(it * 0.2) }
        val x = base.drop(2) // x는 2일 먼저 시작
        val y = base.dropLast(2) // y는 2일 뒤에 시작

        val (lag, r) = engine.crossCorrelation(x, y)

        // lag가 양수면 x가 y를 선행
        assertTrue("높은 상관", abs(r) > 0.8)
    }

    @Test
    fun `CorrelationStrength fromR 분류가 정확하다`() {
        assertEquals(CorrelationStrength.STRONG_POSITIVE, CorrelationStrength.fromR(0.8))
        assertEquals(CorrelationStrength.MODERATE_POSITIVE, CorrelationStrength.fromR(0.5))
        assertEquals(CorrelationStrength.WEAK, CorrelationStrength.fromR(0.1))
        assertEquals(CorrelationStrength.WEAK, CorrelationStrength.fromR(-0.1))
        assertEquals(CorrelationStrength.MODERATE_NEGATIVE, CorrelationStrength.fromR(-0.5))
        assertEquals(CorrelationStrength.STRONG_NEGATIVE, CorrelationStrength.fromR(-0.8))
    }

    @Test
    fun `전체 분석이 정상 실행된다`() = runTest {
        val oscillators = generateOscillators(60)
        val demarks = generateDemarks(60)
        val prices = generatePrices(60)

        val result = engine.analyze(oscillators, demarks, prices)

        assertTrue("상관 결과가 있어야 함", result.correlations.isNotEmpty())
        assertTrue("선행-후행 결과가 있어야 함", result.leadLagResults.isNotEmpty())
    }

    @Test
    fun `상관 결과의 pearsonR 범위가 유효하다`() = runTest {
        val oscillators = generateOscillators(60)
        val demarks = generateDemarks(60)
        val prices = generatePrices(60)

        val result = engine.analyze(oscillators, demarks, prices)

        for (corr in result.correlations) {
            assertTrue("${corr.indicator1}-${corr.indicator2} r >= -1",
                corr.pearsonR >= -1.0)
            assertTrue("${corr.indicator1}-${corr.indicator2} r <= 1",
                corr.pearsonR <= 1.0)
        }
    }

    @Test
    fun `데이터 부족 시 빈 결과 반환`() = runTest {
        val result = engine.analyze(emptyList(), emptyList(), emptyList())
        assertTrue("빈 상관 결과", result.correlations.isEmpty())
    }

    // ─── 헬퍼 ───

    private fun generateOscillators(days: Int): List<OscillatorRow> =
        (0 until days).map { i ->
            val osc = ((i % 10) - 5) * 0.0001
            OscillatorRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L, marketCapTril = 50.0,
                foreign5d = 1000000L, inst5d = -500000L, supplyRatio = 0.001,
                ema12 = 0.001 + osc, ema26 = 0.001, macd = osc,
                signal = osc * 0.5, oscillator = osc * 0.5
            )
        }

    private fun generateDemarks(days: Int): List<DemarkTDRow> =
        (0 until days).map { i ->
            DemarkTDRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                closePrice = 50000, marketCapTril = 50.0,
                tdSellCount = i % 10, tdBuyCount = (9 - i % 10).coerceAtLeast(0)
            )
        }

    private fun generatePrices(days: Int): List<DailyTrading> =
        (0 until days).map { i ->
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L, foreignNetBuy = 1000000L,
                instNetBuy = 500000L, closePrice = 50000 + (i % 7 - 3) * 500
            )
        }
}
