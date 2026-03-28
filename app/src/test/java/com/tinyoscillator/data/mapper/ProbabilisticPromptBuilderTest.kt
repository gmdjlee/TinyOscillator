package com.tinyoscillator.data.mapper

import com.tinyoscillator.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProbabilisticPromptBuilderTest {

    private lateinit var builder: ProbabilisticPromptBuilder

    @Before
    fun setup() {
        builder = ProbabilisticPromptBuilder()
    }

    @Test
    fun `빈 결과에서 유효한 프롬프트 생성`() {
        val result = StatisticalResult(ticker = "005930", stockName = "삼성전자")
        val prompt = builder.build(result)

        assertTrue("ChatML 시작 포함", prompt.contains("<|im_start|>system"))
        assertTrue("ChatML 끝 포함", prompt.contains("<|im_end|>"))
        assertTrue("종목명 포함", prompt.contains("삼성전자"))
        assertTrue("종목코드 포함", prompt.contains("005930"))
    }

    @Test
    fun `전체 결과로 프롬프트 생성 시 모든 섹션 포함`() {
        val result = createFullResult()
        val prompt = builder.build(result)

        assertTrue("나이브 베이즈 섹션", prompt.contains("나이브 베이즈"))
        assertTrue("로지스틱 섹션", prompt.contains("로지스틱"))
        assertTrue("HMM 섹션", prompt.contains("HMM"))
        assertTrue("패턴 섹션", prompt.contains("패턴"))
        assertTrue("신호 점수 섹션", prompt.contains("신호 점수"))
        assertTrue("베이지안 섹션", prompt.contains("베이지안"))
    }

    @Test
    fun `null 결과는 해당 섹션 스킵`() {
        val result = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            bayesResult = BayesResult(0.6, 0.2, 0.2, emptyList(), 100),
            logisticResult = null // null
        )
        val prompt = builder.buildUserPrompt(result)

        assertTrue("베이즈 포함", prompt.contains("나이브 베이즈"))
        assertFalse("로지스틱 미포함", prompt.contains("로지스틱"))
    }

    @Test
    fun `시스템 프롬프트에 JSON 스키마 포함`() {
        val systemPrompt = builder.buildSystemPrompt()

        assertTrue("JSON 스키마 포함", systemPrompt.contains("overall_assessment"))
        assertTrue("confidence 포함", systemPrompt.contains("confidence"))
        assertTrue("한국어 지시", systemPrompt.contains("한국어"))
        assertTrue("재계산 금지", systemPrompt.contains("NEVER recalculate"))
    }

    @Test
    fun `토큰 수 추정이 합리적 범위`() {
        val result = createFullResult()
        val prompt = builder.build(result)
        val tokens = builder.estimateTokenCount(prompt)

        assertTrue("토큰 > 0", tokens > 0)
        assertTrue("토큰 < 5000 (4000 목표)", tokens < 5000)
    }

    @Test
    fun `빈 결과의 토큰이 더 적다`() {
        val emptyResult = StatisticalResult(ticker = "005930", stockName = "삼성전자")
        val fullResult = createFullResult()

        val emptyTokens = builder.estimateTokenCount(builder.build(emptyResult))
        val fullTokens = builder.estimateTokenCount(builder.build(fullResult))

        assertTrue("빈 결과 토큰 < 전체 결과 토큰", emptyTokens < fullTokens)
    }

    @Test
    fun `확률값이 퍼센트로 표시된다`() {
        val result = StatisticalResult(
            ticker = "005930",
            stockName = "삼성전자",
            bayesResult = BayesResult(0.65, 0.20, 0.15, emptyList(), 50)
        )
        val prompt = builder.buildUserPrompt(result)

        assertTrue("65.0% 포함", prompt.contains("65.0%"))
    }

    private fun createFullResult() = StatisticalResult(
        ticker = "005930",
        stockName = "삼성전자",
        bayesResult = BayesResult(
            0.6, 0.25, 0.15,
            listOf(FeatureContribution("MACD", 1.5), FeatureContribution("EMA", 1.2)),
            200
        ),
        logisticResult = LogisticResult(0.72, mapOf("macd" to 0.3), mapOf("macd" to 0.8), 72),
        hmmResult = HmmResult(0, doubleArrayOf(0.5, 0.2, 0.2, 0.1), mapOf("저변동→저변동" to 0.93), listOf(0, 0, 1, 0), "저변동 상승"),
        patternAnalysis = PatternAnalysis(
            listOf(PatternMatch("TRIPLE_BULLISH", "트리플 불리시", true, emptyList(), 0.6, 0.55, 0.5, 0.03, 0.02, 0.01, 0.05, 10)),
            listOf(PatternMatch("TRIPLE_BULLISH", "트리플 불리시", true, emptyList(), 0.6, 0.55, 0.5, 0.03, 0.02, 0.01, 0.05, 10)),
            365
        ),
        signalScoringResult = SignalScoringResult(75, emptyList(), emptyList(), "BULLISH"),
        correlationAnalysis = CorrelationAnalysis(
            listOf(CorrelationResult("수급", "MACD", 0.65, CorrelationStrength.MODERATE_POSITIVE)),
            emptyList()
        ),
        bayesianUpdateResult = BayesianUpdateResult(
            0.68, 0.52,
            listOf(ProbabilityUpdate("MACD=POSITIVE", 0.52, 0.60, 0.08, 1.5))
        )
    )
}
