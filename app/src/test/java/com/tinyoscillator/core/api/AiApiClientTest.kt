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
