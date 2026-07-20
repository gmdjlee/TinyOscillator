package com.tinyoscillator.data.engine

import android.content.SharedPreferences
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.DemarkTDRow
import com.tinyoscillator.domain.model.OscillatorRow
import com.tinyoscillator.domain.repository.FundamentalSnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LogisticScoringEngineTest {

    private lateinit var engine: LogisticScoringEngine
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private val storedValues = mutableMapOf<String, Any>()

    @Before
    fun setup() {
        editor = mockk(relaxed = true)
        every { editor.putFloat(any(), any()) } answers {
            storedValues[firstArg()] = secondArg<Float>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            storedValues[firstArg()] = secondArg<Boolean>()
            editor
        }

        prefs = mockk(relaxed = true)
        every { prefs.edit() } returns editor
        every { prefs.getBoolean(any(), any()) } answers {
            storedValues[firstArg()] as? Boolean ?: secondArg()
        }
        every { prefs.getFloat(any(), any()) } answers {
            storedValues[firstArg()] as? Float ?: secondArg()
        }

        engine = LogisticScoringEngine(prefs)
    }

    @Test
    fun `sigmoid 0은 0점5이다`() {
        assertEquals(0.5, engine.sigmoid(0.0), 0.001)
    }

    @Test
    fun `sigmoid 양수는 0점5 초과이다`() {
        assertTrue(engine.sigmoid(1.0) > 0.5)
        assertTrue(engine.sigmoid(5.0) > 0.5)
    }

    @Test
    fun `sigmoid 음수는 0점5 미만이다`() {
        assertTrue(engine.sigmoid(-1.0) < 0.5)
        assertTrue(engine.sigmoid(-5.0) < 0.5)
    }

    @Test
    fun `sigmoid 범위가 0에서 1이다`() {
        assertTrue(engine.sigmoid(-500.0) >= 0.0)
        assertTrue(engine.sigmoid(500.0) <= 1.0)
        assertTrue(engine.sigmoid(-1000.0) >= 0.0)
        assertTrue(engine.sigmoid(1000.0) <= 1.0)
    }

    @Test
    fun `예측 확률이 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        val result = engine.analyze(prices, oscillators, null, emptyList(), "TEST")

        assertTrue("probability >= 0", result.probability >= 0.0)
        assertTrue("probability <= 1", result.probability <= 1.0)
    }

    @Test
    fun `score0to100이 0에서 100 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        val result = engine.analyze(prices, oscillators, null, emptyList(), "TEST")

        assertTrue("score >= 0", result.score0to100 >= 0)
        assertTrue("score <= 100", result.score0to100 <= 100)
    }

    @Test
    fun `weights 맵이 모든 feature를 포함한다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        val result = engine.analyze(prices, oscillators, null, emptyList(), "TEST")

        for (name in LogisticScoringEngine.FEATURE_NAMES) {
            assertTrue("weights에 $name 포함", result.weights.containsKey(name))
            assertTrue("featureValues에 $name 포함", result.featureValues.containsKey(name))
        }
    }

    @Test
    fun `학습 후 가중치가 저장된다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        engine.trainWeights(prices, oscillators, null, emptyList(), "TEST")

        verify { editor.putBoolean("logistic_trained_TEST", true) }
        verify { editor.apply() }
    }

    // ─── P4 회귀 테스트 ───

    @Test
    fun `종목별 가중치 분리 - 한 종목 학습이 다른 종목으로 전이되지 않는다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        // 종목 A 분석 → A만 학습됨
        engine.analyze(prices, oscillators, null, emptyList(), "AAAAAA")
        assertTrue("A는 학습됨", storedValues["logistic_trained_AAAAAA"] == true)
        // B는 A 학습과 무관하게 여전히 미학습 (전역 플래그였다면 true로 오염됐을 것)
        assertNull("B는 아직 미학습", storedValues["logistic_trained_BBBBBB"])

        // 종목 B 분석 → 자체 학습 트리거
        engine.analyze(prices, oscillators, null, emptyList(), "BBBBBB")
        verify { editor.putBoolean("logistic_trained_BBBBBB", true) }
    }

    @Test
    fun `학습 시 DeMark setup 피처가 gradient에 반영된다 - 죽은 피처 방지`() = runTest {
        val prices = generatePrices(120)
        val oscillators = generateOscillators(120)
        // DeMark buy setup을 20일 후 상승 여부와 상관되게 구성 → 피처가 살아있으면 가중치 학습됨
        val demarkRows = prices.mapIndexed { i, p ->
            val future = prices.getOrNull(i + 20)?.closePrice ?: p.closePrice
            DemarkTDRow(
                date = p.date,
                closePrice = p.closePrice,
                marketCapTril = 50.0,
                tdSellCount = 0,
                tdBuyCount = if (future > p.closePrice) 9 else 0
            )
        }

        engine.trainWeights(prices, oscillators, null, demarkRows, "DEMARK")

        val demarkIdx = LogisticScoringEngine.FEATURE_NAMES.indexOf("demark_buy_setup")
        val w = storedValues["logistic_weight_DEMARK_$demarkIdx"] as? Float
        assertNotNull("demark 가중치 키가 저장되어야 함", w)
        // 버그(학습 시 setup=0 고정) 시 gradient가 항상 0 → weight 정확히 0f. 수정 후엔 0이 아니어야 함.
        assertNotEquals("demark 피처가 학습되어야 함 (0이면 죽은 피처)", 0f, w!!)
    }

    @Test
    fun `featureValues가 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        val result = engine.analyze(prices, oscillators, null, emptyList(), "TEST")

        for ((name, value) in result.featureValues) {
            assertTrue("$name >= 0", value >= 0.0)
            assertTrue("$name <= 1", value <= 1.0)
        }
    }

    // ─── 헬퍼 ───

    private fun generatePrices(days: Int): List<DailyTrading> {
        return (0 until days).map { i ->
            val variation = ((i % 7) - 3) * 500
            DailyTrading(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = (50000 + variation) * 1000000L,
                foreignNetBuy = (variation * 1000).toLong(),
                instNetBuy = (-variation * 500).toLong(),
                closePrice = 50000 + variation
            )
        }
    }

    private fun generateOscillators(days: Int): List<OscillatorRow> {
        return (0 until days).map { i ->
            val osc = ((i % 10) - 5) * 0.0001
            OscillatorRow(
                date = String.format("2025%02d%02d", (i / 28) + 1, (i % 28) + 1),
                marketCap = 50000000000L,
                marketCapTril = 50.0,
                foreign5d = 1000000L,
                inst5d = -500000L,
                supplyRatio = 0.001,
                ema12 = 0.001 + osc,
                ema26 = 0.001,
                macd = osc,
                signal = osc * 0.5,
                oscillator = osc * 0.5
            )
        }
    }
}
