package com.tinyoscillator.core.api

import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.domain.model.ClaudeResponse
import com.tinyoscillator.domain.model.ClaudeContent
import com.tinyoscillator.domain.model.ClaudeUsage
import com.tinyoscillator.domain.model.ClaudeModelsResponse
import com.tinyoscillator.domain.model.ClaudeModelEntry
import com.tinyoscillator.domain.model.GeminiCandidate
import com.tinyoscillator.domain.model.GeminiContent
import com.tinyoscillator.domain.model.GeminiPart
import com.tinyoscillator.domain.model.GeminiResponse
import com.tinyoscillator.domain.model.GeminiUsageMetadata
import com.tinyoscillator.domain.model.GeminiModelsResponse
import com.tinyoscillator.domain.model.GeminiModelEntry
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AiApiClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // --- Claude response parsing ---

    @Test
    fun `Claude response parses text content correctly`() {
        val jsonStr = """
            {
                "id": "msg_01",
                "type": "message",
                "content": [{"type": "text", "text": "분석: 상승 추세입니다."}],
                "usage": {"input_tokens": 120, "output_tokens": 50}
            }
        """.trimIndent()

        val response = json.decodeFromString<ClaudeResponse>(jsonStr)
        assertEquals("msg_01", response.id)
        assertEquals("text", response.content[0].type)
        assertEquals("분석: 상승 추세입니다.", response.content[0].text)
        assertEquals(120, response.usage.inputTokens)
        assertEquals(50, response.usage.outputTokens)
    }

    @Test
    fun `Claude response with empty content`() {
        val jsonStr = """{"id": "msg_02", "content": [], "usage": {"input_tokens": 10, "output_tokens": 0}}"""
        val response = json.decodeFromString<ClaudeResponse>(jsonStr)
        assertTrue(response.content.isEmpty())
    }

    @Test
    fun `Claude response with multiple content blocks`() {
        val jsonStr = """
            {
                "id": "msg_03",
                "content": [
                    {"type": "text", "text": "첫 번째"},
                    {"type": "text", "text": "두 번째"}
                ],
                "usage": {"input_tokens": 100, "output_tokens": 200}
            }
        """.trimIndent()
        val response = json.decodeFromString<ClaudeResponse>(jsonStr)
        assertEquals(2, response.content.size)
        assertEquals("첫 번째", response.content[0].text)
    }

    // --- Gemini response parsing ---

    @Test
    fun `Gemini response parses correctly`() {
        val jsonStr = """
            {
                "candidates": [{
                    "content": {
                        "parts": [{"text": "Gemini: 시장 분석 완료"}],
                        "role": "model"
                    }
                }],
                "usageMetadata": {
                    "promptTokenCount": 80,
                    "candidatesTokenCount": 40
                }
            }
        """.trimIndent()

        val response = json.decodeFromString<GeminiResponse>(jsonStr)
        assertEquals("Gemini: 시장 분석 완료", response.candidates[0].content.parts[0].text)
        assertEquals(80, response.usageMetadata.promptTokenCount)
        assertEquals(40, response.usageMetadata.candidatesTokenCount)
    }

    @Test
    fun `Gemini response with empty candidates`() {
        val jsonStr = """{"candidates": [], "usageMetadata": {"promptTokenCount": 0, "candidatesTokenCount": 0}}"""
        val response = json.decodeFromString<GeminiResponse>(jsonStr)
        assertTrue(response.candidates.isEmpty())
    }

    // --- Claude SSE streaming event parsing ---

    @Test
    fun `Claude stream message_start carries input and cache tokens`() {
        val jsonStr = """
            {"type":"message_start","message":{"id":"msg_1","usage":{"input_tokens":4200,"output_tokens":1,"cache_read_input_tokens":3900}}}
        """.trimIndent()
        val event = json.decodeFromString<com.tinyoscillator.domain.model.ClaudeStreamEvent>(jsonStr)
        assertEquals("message_start", event.type)
        assertEquals(4200, event.message?.usage?.inputTokens)
        assertEquals(3900, event.message?.usage?.cacheReadInputTokens)
    }

    @Test
    fun `Claude stream content_block_delta carries text delta`() {
        val jsonStr = """
            {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"상승 "}}
        """.trimIndent()
        val event = json.decodeFromString<com.tinyoscillator.domain.model.ClaudeStreamEvent>(jsonStr)
        assertEquals("content_block_delta", event.type)
        assertEquals("text_delta", event.delta?.type)
        assertEquals("상승 ", event.delta?.text)
    }

    @Test
    fun `Claude stream message_delta carries output tokens`() {
        val jsonStr = """
            {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":512}}
        """.trimIndent()
        val event = json.decodeFromString<com.tinyoscillator.domain.model.ClaudeStreamEvent>(jsonStr)
        assertEquals("message_delta", event.type)
        assertEquals(512, event.usage?.outputTokens)
    }

    // --- Claude structured output (tool_use) parsing ---

    @Test
    fun `Claude response parses tool_use input as JSON object`() {
        val jsonStr = """
            {
                "id": "msg_04",
                "content": [{
                    "type": "tool_use",
                    "id": "toolu_01",
                    "name": "submit_analysis",
                    "input": {"overall_assessment": "매수 우위", "confidence": 0.72, "action": "분할 매수"}
                }],
                "usage": {"input_tokens": 900, "output_tokens": 150, "cache_creation_input_tokens": 800}
            }
        """.trimIndent()
        val response = json.decodeFromString<ClaudeResponse>(jsonStr)
        val toolUse = response.content.first { it.type == "tool_use" }
        assertNotNull(toolUse.input)
        assertTrue(toolUse.input.toString().contains("매수 우위"))
        assertEquals(800, response.usage.cacheCreationInputTokens)
    }

    // --- Config validation ---

    @Test
    fun `AiApiKeyConfig validates correctly`() {
        assertTrue(AiApiKeyConfig(AiProvider.CLAUDE, "key", "model").isValid())
        assertFalse(AiApiKeyConfig(AiProvider.CLAUDE, "", "model").isValid())
        assertFalse(AiApiKeyConfig(AiProvider.CLAUDE, "key", "").isValid())
        assertFalse(AiApiKeyConfig(AiProvider.CLAUDE, "   ", "model").isValid())
    }

    @Test
    fun `AiApiKeyConfig base URLs are correct`() {
        val claude = AiApiKeyConfig(AiProvider.CLAUDE, "k", "m")
        val gemini = AiApiKeyConfig(AiProvider.GEMINI, "k", "m")

        assertEquals("https://api.anthropic.com", claude.getBaseUrl())
        assertEquals("https://generativelanguage.googleapis.com", gemini.getBaseUrl())
    }

    @Test
    fun `AiProvider has display names`() {
        AiProvider.entries.forEach { provider ->
            assertTrue(provider.displayName.isNotBlank())
        }
    }

    @Test
    fun `Claude response ignores unknown keys`() {
        val jsonStr = """
            {
                "id": "msg_x",
                "type": "message",
                "model": "claude-haiku-4-5-20251001",
                "stop_reason": "end_turn",
                "content": [{"type": "text", "text": "OK"}],
                "usage": {"input_tokens": 1, "output_tokens": 2, "cache_creation_input_tokens": 0}
            }
        """.trimIndent()
        val response = json.decodeFromString<ClaudeResponse>(jsonStr)
        assertEquals("OK", response.content[0].text)
    }

    // --- Models API response parsing ---

    @Test
    fun `Claude models response parses correctly`() {
        val jsonStr = """
            {
                "data": [
                    {"id": "claude-sonnet-4-6", "display_name": "Claude Sonnet 4.6", "type": "model"},
                    {"id": "claude-haiku-4-5-20251001", "display_name": "Claude Haiku 4.5"}
                ],
                "has_more": false
            }
        """.trimIndent()
        val response = json.decodeFromString<ClaudeModelsResponse>(jsonStr)
        assertEquals(2, response.data.size)
        assertEquals("claude-sonnet-4-6", response.data[0].id)
    }

    @Test
    fun `Gemini models response filters generateContent models`() {
        val jsonStr = """
            {
                "models": [
                    {"name": "models/gemini-2.5-flash", "displayName": "Gemini 2.5 Flash",
                     "supportedGenerationMethods": ["generateContent", "countTokens"]},
                    {"name": "models/text-embedding-004", "displayName": "Embedding",
                     "supportedGenerationMethods": ["embedContent"]}
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<GeminiModelsResponse>(jsonStr)
        val generativeModels = response.models.filter { "generateContent" in it.supportedGenerationMethods }
        assertEquals(1, generativeModels.size)
        assertEquals("models/gemini-2.5-flash", generativeModels[0].name)
        // id 추출 시 "models/" prefix 제거
        assertEquals("gemini-2.5-flash", generativeModels[0].name.removePrefix("models/"))
    }
}
