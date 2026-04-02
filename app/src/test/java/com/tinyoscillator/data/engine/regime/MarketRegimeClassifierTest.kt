package com.tinyoscillator.data.engine.regime

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class MarketRegimeClassifierTest {

    private lateinit var classifier: MarketRegimeClassifier

    @Before
    fun setup() {
        classifier = MarketRegimeClassifier()
    }

    // ─── buildFeatures ───

    @Test
    fun `buildFeatures에 NaN이 없다`() {
        val closes = generateKospiLikeData(200)
        val features = classifier.buildFeatures(closes)

        assertTrue("특성 행렬이 비어있지 않아야 함", features.isNotEmpty())
        for ((idx, row) in features.withIndex()) {
            assertEquals("특성 벡터 차원 = 4", 4, row.size)
            for ((d, v) in row.withIndex()) {
                assertTrue("features[$idx][$d]가 NaN이면 안됨", !v.isNaN())
                assertTrue("features[$idx][$d]가 Inf이면 안됨", v.isFinite())
            }
        }
    }

    @Test
    fun `buildFeatures가 warmup 이후에만 결과를 생성한다`() {
        val closes = generateKospiLikeData(200)
        val features = classifier.buildFeatures(closes)

        // 200 closes → 199 returns → 199 - 60 (lookback) = 139 usable features
        assertTrue("warmup 이후 특성 수: ${features.size}", features.size <= 199 - MarketRegimeClassifier.LOOKBACK + 1)
        assertTrue("특성이 존재해야 함", features.size > 50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `데이터 부족 시 buildFeatures 예외`() {
        val closes = DoubleArray(30) { 2500.0 + it }
        classifier.buildFeatures(closes)
    }

    // ─── fit & predict ───

    @Test
    fun `fit 후 predictRegime이 유효한 레짐을 반환한다`() {
        val closes = generateKospiLikeData(300)
        classifier.fit(closes)

        val result = classifier.predictRegime(closes)

        assertTrue("regimeId 범위", result.regimeId in 0..3)
        assertTrue("regimeName 유효", result.regimeName in MarketRegimeClassifier.REGIME_NAMES)
        assertTrue("confidence 범위 0~1", result.confidence in 0.0..1.0)
        assertEquals("probaVec 크기 = 4", 4, result.probaVec.size)
        assertTrue("probaVec 합 ≈ 1.0", abs(result.probaVec.sum() - 1.0) < 0.01)
        assertTrue("regimeDurationDays > 0", result.regimeDurationDays > 0)
    }

    @Test
    fun `predictRegime이 4개 이름 중 하나를 반환한다`() {
        val closes = generateKospiLikeData(300)
        classifier.fit(closes)
        val result = classifier.predictRegime(closes)

        val validNames = setOf("BULL_LOW_VOL", "BEAR_HIGH_VOL", "SIDEWAYS", "CRISIS")
        assertTrue("${result.regimeName} ∈ $validNames", result.regimeName in validNames)
    }

    // ─── save/load roundtrip ───

    @Test
    fun `saveModel-loadModel 라운드트립이 predict를 보존한다`() {
        val closes = generateKospiLikeData(300)
        classifier.fit(closes)

        val resultBefore = classifier.predictRegime(closes)

        // Save
        val state = classifier.saveModel()

        // Create new classifier and load
        val restored = MarketRegimeClassifier()
        restored.loadModel(state)

        val resultAfter = restored.predictRegime(closes)

        assertEquals("regimeId 보존", resultBefore.regimeId, resultAfter.regimeId)
        assertEquals("regimeName 보존", resultBefore.regimeName, resultAfter.regimeName)
        assertEquals("confidence 보존", resultBefore.confidence, resultAfter.confidence, 1e-6)
        assertEquals("duration 보존", resultBefore.regimeDurationDays, resultAfter.regimeDurationDays)
    }

    // ─── regime duration ───

    @Test
    fun `일관된 데이터에서 레짐 지속 기간이 올바르게 증가한다`() {
        // Generate very stable uptrend data → should mostly be in one regime
        val closes = DoubleArray(300) { 2500.0 * (1.0 + 0.001 * it) } // steady uptrend
        classifier.fit(closes)

        val result = classifier.predictRegime(closes)

        // Duration should be substantial for uniform data
        assertTrue("일관된 데이터에서 지속 기간 > 10일: ${result.regimeDurationDays}",
            result.regimeDurationDays > 10)
    }

    // ─── heuristic fallback ───

    @Test
    fun `학습 전 heuristic fallback이 유효한 결과를 반환한다`() {
        val closes = generateKospiLikeData(200)
        val result = classifier.predictRegime(closes)

        assertTrue("regimeId 범위", result.regimeId in 0..3)
        assertTrue("regimeName 유효", result.regimeName in MarketRegimeClassifier.REGIME_NAMES)
        assertTrue("confidence > 0", result.confidence > 0.0)
        assertEquals("probaVec 크기 = 4", 4, result.probaVec.size)
    }

    @Test
    fun `trained 플래그가 fit 후 true가 된다`() {
        assertFalse("초기 상태: trained = false", classifier.trained)

        val closes = generateKospiLikeData(300)
        classifier.fit(closes)

        assertTrue("학습 후: trained = true", classifier.trained)
    }

    // ─── Helper ───

    private fun generateKospiLikeData(days: Int): DoubleArray {
        // Simulate KOSPI-like index: ~2500 base, daily returns ~0.5%, some regime changes
        var price = 2500.0
        val rng = kotlin.random.Random(42)
        return DoubleArray(days) { i ->
            val regime = i / (days / 4)
            val dailyReturn = when (regime) {
                0 -> 0.003 + rng.nextDouble(-0.008, 0.008)   // bull
                1 -> -0.002 + rng.nextDouble(-0.015, 0.015)  // bear high vol
                2 -> 0.0005 + rng.nextDouble(-0.005, 0.005)  // sideways
                else -> -0.005 + rng.nextDouble(-0.025, 0.025) // crisis
            }
            price *= (1.0 + dailyReturn)
            price
        }
    }
}
