package com.tinyoscillator.data.mapper

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AnalysisResponseParserTest {

    private lateinit var parser: AnalysisResponseParser

    @Before
    fun setup() {
        parser = AnalysisResponseParser()
    }

    @Test
    fun `정상 JSON 파싱`() {
        val json = """
        {
            "overall_assessment": "매수 - 기술적 지표 긍정적",
            "confidence": 0.75,
            "insights": [
                {"algorithm": "NaiveBayes", "interpretation": "상승 확률 높음", "significance": "높음"}
            ],
            "conflicts": ["MACD와 DeMark 충돌"],
            "risks": ["고변동 구간 진입 가능"],
            "action": "분할 매수 추천"
        }
        """.trimIndent()

        val result = parser.parse(json)

        assertEquals("매수 - 기술적 지표 긍정적", result.overallAssessment)
        assertEquals(0.75, result.confidence, 0.001)
        assertEquals(1, result.insights.size)
        assertEquals("NaiveBayes", result.insights[0].algorithmName)
        assertEquals(1, result.conflicts.size)
        assertEquals(1, result.risks.size)
        assertEquals("분할 매수 추천", result.action)
    }

    @Test
    fun `마크다운 래핑된 JSON 파싱`() {
        val response = """
        분석 결과입니다:
        ```json
        {
            "overall_assessment": "관망",
            "confidence": 0.5,
            "insights": [],
            "conflicts": [],
            "risks": [],
            "action": "대기"
        }
        ```
        """.trimIndent()

        val result = parser.parse(response)

        assertEquals("관망", result.overallAssessment)
        assertEquals(0.5, result.confidence, 0.001)
        assertEquals("대기", result.action)
    }

    @Test
    fun `비정상 출력은 fallback 반환`() {
        val response = "이것은 JSON이 아닌 일반 텍스트입니다."

        val result = parser.parse(response)

        assertEquals("LLM 응답 파싱 실패", result.overallAssessment)
        assertEquals(0.0, result.confidence, 0.001)
        assertTrue("원본 텍스트 포함", result.summary.contains("일반 텍스트"))
    }

    @Test
    fun `빈 응답은 fallback 반환`() {
        val result = parser.parse("")

        assertEquals("LLM 응답 파싱 실패", result.overallAssessment)
        assertEquals(0.0, result.confidence, 0.001)
    }

    @Test
    fun `confidence 범위 검증 - 초과값 보정`() {
        val json = """{"overall_assessment": "테스트", "confidence": 1.5, "action": "test"}"""

        val result = parser.parse(json)

        assertEquals("confidence는 1.0으로 보정", 1.0, result.confidence, 0.001)
    }

    @Test
    fun `confidence 범위 검증 - 음수값 보정`() {
        val json = """{"overall_assessment": "테스트", "confidence": -0.5, "action": "test"}"""

        val result = parser.parse(json)

        assertEquals("confidence는 0.0으로 보정", 0.0, result.confidence, 0.001)
    }

    @Test
    fun `필수 필드 누락 시 기본값`() {
        val json = """{"confidence": 0.7}"""

        val result = parser.parse(json)

        assertEquals("분석 불가", result.overallAssessment)
        assertEquals("추가 분석 필요", result.action)
    }

    @Test
    fun `JSON 앞뒤 텍스트 무시`() {
        val response = "아래는 분석 결과입니다:\n{\"overall_assessment\": \"매도\", \"confidence\": 0.8, \"action\": \"매도\"}\n감사합니다."

        val result = parser.parse(response)

        assertEquals("매도", result.overallAssessment)
        assertEquals(0.8, result.confidence, 0.001)
    }

    @Test
    fun `extractJson이 올바른 범위 추출`() {
        val text = "some text {\"key\": \"value\"} more text"
        val json = parser.extractJson(text)

        assertEquals("{\"key\": \"value\"}", json)
    }

    @Test
    fun `extractJson에서 중괄호 없으면 null`() {
        val text = "no json here"
        assertNull(parser.extractJson(text))
    }

    @Test
    fun `여러 insights 파싱`() {
        val json = """
        {
            "overall_assessment": "매수",
            "confidence": 0.8,
            "insights": [
                {"algorithm": "Bayes", "interpretation": "상승", "significance": "높음"},
                {"algorithm": "HMM", "interpretation": "저변동", "significance": "보통"},
                {"algorithm": "Pattern", "interpretation": "골든크로스", "significance": "높음"}
            ],
            "conflicts": [],
            "risks": [],
            "action": "매수"
        }
        """.trimIndent()

        val result = parser.parse(json)

        assertEquals(3, result.insights.size)
        assertEquals("Bayes", result.insights[0].algorithmName)
        assertEquals("HMM", result.insights[1].algorithmName)
    }
}
