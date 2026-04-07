package com.tinyoscillator.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- AiApiKeyConfig ---

    @Test
    fun `AiApiKeyConfig isValid returns true for non-blank key and modelId`() {
        val config = AiApiKeyConfig(AiProvider.CLAUDE, "sk-ant-test-key", "claude-sonnet-4-6")
        assertTrue(config.isValid())
    }

    @Test
    fun `AiApiKeyConfig isValid returns false for blank key`() {
        val config = AiApiKeyConfig(AiProvider.CLAUDE, "", "claude-sonnet-4-6")
        assertFalse(config.isValid())
    }

    @Test
    fun `AiApiKeyConfig isValid returns false for blank modelId`() {
        val config = AiApiKeyConfig(AiProvider.CLAUDE, "key", "")
        assertFalse(config.isValid())
    }

    @Test
    fun `AiApiKeyConfig getBaseUrl returns anthropic for Claude`() {
        val claude = AiApiKeyConfig(AiProvider.CLAUDE, "key", "model")
        assertTrue(claude.getBaseUrl().contains("anthropic"))
    }

    @Test
    fun `AiApiKeyConfig getBaseUrl returns google for Gemini`() {
        val gemini = AiApiKeyConfig(AiProvider.GEMINI, "key", "model")
        assertTrue(gemini.getBaseUrl().contains("googleapis"))
    }

    // --- AiProvider ---

    @Test
    fun `AiProvider has exactly two entries`() {
        assertEquals(2, AiProvider.entries.size)
        assertEquals("Claude", AiProvider.CLAUDE.displayName)
        assertEquals("Gemini", AiProvider.GEMINI.displayName)
    }

    // --- ClaudeResponse serialization ---

    @Test
    fun `ClaudeResponse deserializes correctly`() {
        val jsonStr = """
            {
                "id": "msg_123",
                "content": [{"type": "text", "text": "분석 결과입니다."}],
                "usage": {"input_tokens": 100, "output_tokens": 200}
            }
        """.trimIndent()
        val response = json.decodeFromString<ClaudeResponse>(jsonStr)
        assertEquals("msg_123", response.id)
        assertEquals(1, response.content.size)
        assertEquals("text", response.content[0].type)
        assertEquals("분석 결과입니다.", response.content[0].text)
        assertEquals(100, response.usage.inputTokens)
        assertEquals(200, response.usage.outputTokens)
    }

    // --- GeminiResponse serialization ---

    @Test
    fun `GeminiResponse deserializes correctly`() {
        val jsonStr = """
            {
                "candidates": [{
                    "content": {
                        "parts": [{"text": "Gemini 분석 결과"}]
                    }
                }],
                "usageMetadata": {
                    "promptTokenCount": 50,
                    "candidatesTokenCount": 150
                }
            }
        """.trimIndent()
        val response = json.decodeFromString<GeminiResponse>(jsonStr)
        assertEquals(1, response.candidates.size)
        assertEquals("Gemini 분석 결과", response.candidates[0].content.parts[0].text)
        assertEquals(50, response.usageMetadata.promptTokenCount)
        assertEquals(150, response.usageMetadata.candidatesTokenCount)
    }

    // --- Claude Models API response ---

    @Test
    fun `ClaudeModelsResponse deserializes correctly`() {
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
        assertEquals("Claude Sonnet 4.6", response.data[0].displayName)
    }

    // --- Gemini Models API response ---

    @Test
    fun `GeminiModelsResponse deserializes correctly`() {
        val jsonStr = """
            {
                "models": [
                    {
                        "name": "models/gemini-2.5-flash",
                        "displayName": "Gemini 2.5 Flash",
                        "supportedGenerationMethods": ["generateContent", "countTokens"]
                    },
                    {
                        "name": "models/text-embedding-004",
                        "displayName": "Text Embedding 004",
                        "supportedGenerationMethods": ["embedContent"]
                    }
                ]
            }
        """.trimIndent()
        val response = json.decodeFromString<GeminiModelsResponse>(jsonStr)
        assertEquals(2, response.models.size)
        assertEquals("models/gemini-2.5-flash", response.models[0].name)
        assertEquals("Gemini 2.5 Flash", response.models[0].displayName)
        assertTrue("generateContent" in response.models[0].supportedGenerationMethods)
        assertFalse("generateContent" in response.models[1].supportedGenerationMethods)
    }
}
