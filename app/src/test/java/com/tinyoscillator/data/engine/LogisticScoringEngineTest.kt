package com.tinyoscillator.data.engine

import android.content.SharedPreferences
import com.tinyoscillator.domain.model.DailyTrading
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

        val result = engine.analyze(prices, oscillators, null)

        assertTrue("probability >= 0", result.probability >= 0.0)
        assertTrue("probability <= 1", result.probability <= 1.0)
    }

    @Test
    fun `score0to100이 0에서 100 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        val result = engine.analyze(prices, oscillators, null)

        assertTrue("score >= 0", result.score0to100 >= 0)
        assertTrue("score <= 100", result.score0to100 <= 100)
    }

    @Test
    fun `weights 맵이 모든 feature를 포함한다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        val result = engine.analyze(prices, oscillators, null)

        for (name in LogisticScoringEngine.FEATURE_NAMES) {
            assertTrue("weights에 $name 포함", result.weights.containsKey(name))
            assertTrue("featureValues에 $name 포함", result.featureValues.containsKey(name))
        }
    }

    @Test
    fun `학습 후 가중치가 저장된다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        engine.trainWeights(prices, oscillators, null)

        verify { editor.putBoolean("logistic_trained", true) }
        verify { editor.apply() }
    }

    @Test
    fun `featureValues가 0에서 1 범위이다`() = runTest {
        val prices = generatePrices(100)
        val oscillators = generateOscillators(100)

        val result = engine.analyze(prices, oscillators, null)

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
