package com.tinyoscillator.feature.bearsignal.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [parseGeminiLlmResponse]/[parseLlmResponse] 순수 함수 테스트(§4.5 v1.3 "Gemini 경로" +
 * §4.7 검증1 "URL 교차검증" 입력 추출) — Context/네트워크 의존성 없이 응답 body만으로 직접 검증한다.
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

    // ── §4.7 검증1 "URL 교차검증" 입력 — Gemini groundingChunks ───────────────

    @Test
    fun `groundingChunks가 있으면 web uri를 resultUrls로 수집한다`() {
        val body = buildJsonObject {
            put("candidates", buildJsonArray {
                add(buildJsonObject {
                    put("content", buildJsonObject {
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", "hi") }) })
                    })
                    put("groundingMetadata", buildJsonObject {
                        put("groundingChunks", buildJsonArray {
                            add(buildJsonObject { put("web", buildJsonObject { put("uri", "https://a.example.com") }) })
                            add(buildJsonObject { put("web", buildJsonObject { put("uri", "https://b.example.com") }) })
                        })
                    })
                })
            })
        }.toString()

        val parsed = parseGeminiLlmResponse(body, json)

        assertEquals(listOf("https://a.example.com", "https://b.example.com"), parsed.resultUrls)
    }

    @Test
    fun `groundingChunks가 없으면 resultUrls는 빈 리스트다`() {
        val body = geminiResponseBody(parts = listOf("텍스트"))

        val parsed = parseGeminiLlmResponse(body, json)

        assertEquals(emptyList<String>(), parsed.resultUrls)
    }

    // ── §4.7 검증1 "URL 교차검증" 입력 — Claude web_search_tool_result ─────────

    private fun claudeResponseBodyWithWebSearchResult(
        resultContentBlock: JsonElement?,
        stopReason: String = "end_turn"
    ): String = buildJsonObject {
        put("id", "msg_1")
        put("type", "message")
        put("content", buildJsonArray {
            if (resultContentBlock != null) {
                add(buildJsonObject {
                    put("type", "web_search_tool_result")
                    put("tool_use_id", "srvtool_1")
                    put("content", resultContentBlock)
                })
            }
            add(buildJsonObject {
                put("type", "text")
                put("text", "hi")
            })
        })
        put("stop_reason", stopReason)
    }.toString()

    @Test
    fun `web_search_tool_result 블록의 url을 resultUrls로 수집한다`() {
        val resultContent = buildJsonArray {
            add(buildJsonObject {
                put("type", "web_search_result")
                put("url", "https://a.example.com")
                put("title", "A")
            })
            add(buildJsonObject {
                put("type", "web_search_result")
                put("url", "https://b.example.com")
                put("title", "B")
            })
        }
        val body = claudeResponseBodyWithWebSearchResult(resultContent)

        val parsed = parseLlmResponse(body, json)

        assertEquals(listOf("https://a.example.com", "https://b.example.com"), parsed.resultUrls)
    }

    @Test
    fun `web_search_tool_result content가 에러 객체이면 resultUrls는 빈 리스트다`() {
        val errorContent = buildJsonObject {
            put("type", "web_search_tool_result_error")
            put("error_code", "max_uses_exceeded")
        }
        val body = claudeResponseBodyWithWebSearchResult(errorContent)

        val parsed = parseLlmResponse(body, json)

        assertEquals(emptyList<String>(), parsed.resultUrls)
    }

    @Test
    fun `web_search_tool_result 블록이 없으면 resultUrls는 빈 리스트다`() {
        val body = claudeResponseBodyWithWebSearchResult(resultContentBlock = null)

        val parsed = parseLlmResponse(body, json)

        assertEquals(emptyList<String>(), parsed.resultUrls)
    }
}
