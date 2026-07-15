package com.tinyoscillator.feature.bearsignal.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [parseGeminiLlmResponse] 순수 함수 테스트(§4.5 v1.3 "Gemini 경로") — Context/네트워크 의존성 없이
 * Gemini `generateContent` 응답 body만으로 직접 검증한다.
 */
class LlmResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun geminiResponseBody(
        parts: List<String>,
        renderedContent: String? = null
    ): String = buildJsonObject {
        put("candidates", buildJsonArray {
            add(buildJsonObject {
                put("content", buildJsonObject {
                    put("parts", buildJsonArray {
                        parts.forEach { text -> add(buildJsonObject { put("text", text) }) }
                    })
                })
                if (renderedContent != null) {
                    put("groundingMetadata", buildJsonObject {
                        put("searchEntryPoint", buildJsonObject {
                            put("renderedContent", renderedContent)
                        })
                    })
                }
            })
        })
    }.toString()

    @Test
    fun `parts가 여럿이면 순서대로 이어붙인다`() {
        val body = geminiResponseBody(parts = listOf("""{"rate":""", """4.00}"""))

        val parsed = parseGeminiLlmResponse(body, json)

        assertEquals("""{"rate":4.00}""", parsed.finalText)
        assertNull(parsed.searchWidgetHtml)
    }

    @Test
    fun `renderedContent가 있으면 searchWidgetHtml로 추출한다`() {
        val body = geminiResponseBody(
            parts = listOf("""{"credit":40.0}"""),
            renderedContent = "<div>Google 검색 제안 위젯</div>"
        )

        val parsed = parseGeminiLlmResponse(body, json)

        assertEquals("""{"credit":40.0}""", parsed.finalText)
        assertEquals("<div>Google 검색 제안 위젯</div>", parsed.searchWidgetHtml)
    }

    @Test
    fun `candidates가 비어있으면 finalText는 빈 문자열이고 searchWidgetHtml은 null이다`() {
        val body = buildJsonObject { put("candidates", buildJsonArray {}) }.toString()

        val parsed = parseGeminiLlmResponse(body, json)

        assertEquals("", parsed.finalText)
        assertNull(parsed.searchWidgetHtml)
    }

    @Test
    fun `groundingMetadata가 없으면 searchWidgetHtml은 null이다`() {
        val body = geminiResponseBody(parts = listOf("텍스트만"), renderedContent = null)

        val parsed = parseGeminiLlmResponse(body, json)

        assertEquals("텍스트만", parsed.finalText)
        assertNull(parsed.searchWidgetHtml)
    }

    @Test
    fun `candidates 필드 자체가 없으면 finalText는 빈 문자열이다`() {
        val body = buildJsonObject { put("promptFeedback", buildJsonObject {}) }.toString()

        val parsed = parseGeminiLlmResponse(body, json)

        assertEquals("", parsed.finalText)
        assertNull(parsed.searchWidgetHtml)
    }
}
