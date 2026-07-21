package com.tinyoscillator.domain.usecase

import com.tinyoscillator.data.engine.FeatureStore
import com.tinyoscillator.data.engine.RationaleBuilder
import com.tinyoscillator.data.engine.StatisticalAnalysisEngine
import com.tinyoscillator.domain.model.AlgoResult
import com.tinyoscillator.domain.model.StatisticalResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ProbabilityAnalysisUseCase.buildSnapshot] JSON 직렬화 검증 (P8-3).
 *
 * 기존 수제 JSON은 따옴표만 escape → 근거 문자열의 역슬래시·개행·제어문자,
 * 점수의 NaN/Infinity에서 **invalid JSON이 DB에 영속**되던 결함이 있었다.
 * kotlinx.serialization 전환 후 항상 유효 JSON을 산출하는지 확인한다.
 */
class ProbabilityAnalysisUseCaseTest {

    private val engine: StatisticalAnalysisEngine = mockk()
    private val featureStore: FeatureStore = mockk(relaxed = true)
    private lateinit var useCase: ProbabilityAnalysisUseCase

    private val dummyResult: StatisticalResult = mockk()

    @Before
    fun setup() {
        mockkObject(RationaleBuilder)
        useCase = ProbabilityAnalysisUseCase(engine, featureStore)
        every { engine.getEnsembleProbability(any()) } returns 0.6
    }

    @After
    fun tearDown() {
        unmockkObject(RationaleBuilder)
    }

    private fun stubAlgos(map: Map<String, AlgoResult>) {
        every { RationaleBuilder.build(any()) } returns map
    }

    private fun algo(name: String, score: Float, rationale: String) =
        AlgoResult(algoName = name, score = score, rationale = rationale)

    // ── 유효 JSON 산출 ──

    @Test
    fun `따옴표 포함 근거도 유효 JSON으로 직렬화된다`() {
        stubAlgos(mapOf("NaiveBayes" to algo("NaiveBayes", 0.78f, "상승 \"강\" 신호")))

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        // 파싱이 예외 없이 되고 원문 그대로 복원되어야 한다
        val parsed = Json.parseToJsonElement(snapshot.algoRationales) as JsonObject
        assertEquals("상승 \"강\" 신호", parsed["NaiveBayes"]!!.jsonPrimitive.content)
    }

    @Test
    fun `역슬래시 포함 근거도 유효 JSON — 기존 수제 JSON이 깨지던 케이스`() {
        stubAlgos(mapOf("Logistic" to algo("Logistic", 0.87f, "경로 C:\\temp 상승\\하락")))

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        val parsed = Json.parseToJsonElement(snapshot.algoRationales) as JsonObject
        assertEquals("경로 C:\\temp 상승\\하락", parsed["Logistic"]!!.jsonPrimitive.content)
    }

    @Test
    fun `개행·탭 등 제어문자 포함 근거도 유효 JSON`() {
        stubAlgos(mapOf("HMM" to algo("HMM", 0.5f, "1줄\n2줄\t끝")))

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        val parsed = Json.parseToJsonElement(snapshot.algoRationales) as JsonObject
        assertEquals("1줄\n2줄\t끝", parsed["HMM"]!!.jsonPrimitive.content)
    }

    @Test
    fun `NaN 점수는 0으로 정제되어 유효 JSON`() {
        stubAlgos(mapOf("OrderFlow" to algo("OrderFlow", Float.NaN, "결측")))

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        // 파싱 성공(예외 없음) + NaN → 0
        val parsed = Json.parseToJsonElement(snapshot.algoScores) as JsonObject
        assertEquals(0f, parsed["OrderFlow"]!!.jsonPrimitive.content.toFloat(), 0.0001f)
    }

    @Test
    fun `Infinity 점수도 0으로 정제되어 유효 JSON`() {
        stubAlgos(mapOf("Signal" to algo("Signal", Float.POSITIVE_INFINITY, "발산")))

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        val parsed = Json.parseToJsonElement(snapshot.algoScores) as JsonObject
        assertEquals(0f, parsed["Signal"]!!.jsonPrimitive.content.toFloat(), 0.0001f)
    }

    @Test
    fun `정상 점수는 그대로 직렬화되고 값이 보존된다`() {
        stubAlgos(
            mapOf(
                "NaiveBayes" to algo("NaiveBayes", 0.78f, "a"),
                "Logistic" to algo("Logistic", 0.34f, "b")
            )
        )

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        val parsed = Json.parseToJsonElement(snapshot.algoScores) as JsonObject
        assertEquals(0.78f, parsed["NaiveBayes"]!!.jsonPrimitive.content.toFloat(), 0.0001f)
        assertEquals(0.34f, parsed["Logistic"]!!.jsonPrimitive.content.toFloat(), 0.0001f)
    }

    @Test
    fun `빈 알고리즘 맵은 빈 객체 JSON`() {
        stubAlgos(emptyMap())

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        assertEquals("{}", snapshot.algoScores)
        assertEquals("{}", snapshot.algoRationales)
        // 빈 객체도 유효 JSON
        assertTrue(Json.parseToJsonElement(snapshot.algoScores) is JsonObject)
    }

    // ── 스냅샷 메타/앙상블 폴백 ──

    @Test
    fun `스냅샷 메타(티커·이름·앙상블) 채워진다`() {
        stubAlgos(mapOf("NaiveBayes" to algo("NaiveBayes", 0.78f, "a")))

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        assertEquals("005930", snapshot.ticker)
        assertEquals("삼성전자", snapshot.name)
        assertEquals(0.6, snapshot.ensembleScore, 0.0001)
        assertTrue(snapshot.analyzedAt > 0)
    }

    @Test
    fun `앙상블 계산 예외 시 0_5로 폴백`() {
        stubAlgos(mapOf("NaiveBayes" to algo("NaiveBayes", 0.78f, "a")))
        every { engine.getEnsembleProbability(any()) } throws RuntimeException("메타 학습기 미학습")

        val snapshot = useCase.buildSnapshot("005930", "삼성전자", dummyResult)

        assertEquals(0.5, snapshot.ensembleScore, 0.0001)
    }
}
