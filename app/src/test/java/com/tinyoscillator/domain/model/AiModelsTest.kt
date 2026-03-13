package com.tinyoscillator.domain.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AiModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    // --- AiApiKeyConfig ---

    @Test
    fun `AiApiKeyConfig isValid returns true for non-blank key`() {
        val config = AiApiKeyConfig(AiProvider.CLAUDE_HAIKU, "sk-ant-test-key")
        assertTrue(config.isValid())
    }

    @Test
    fun `AiApiKeyConfig isValid returns false for blank key`() {
        val config = AiApiKeyConfig(AiProvider.CLAUDE_HAIKU, "")
        assertFalse(config.isValid())
    }

    @Test
    fun `AiApiKeyConfig getBaseUrl returns anthropic for Claude providers`() {
        val haiku = AiApiKeyConfig(AiProvider.CLAUDE_HAIKU, "key")
        val sonnet = AiApiKeyConfig(AiProvider.CLAUDE_SONNET, "key")
        assertTrue(haiku.getBaseUrl().contains("anthropic"))
        assertTrue(sonnet.getBaseUrl().contains("anthropic"))
    }

    @Test
    fun `AiApiKeyConfig getBaseUrl returns google for Gemini`() {
        val gemini = AiApiKeyConfig(AiProvider.GEMINI_FLASH, "key")
        assertTrue(gemini.getBaseUrl().contains("googleapis"))
    }

    // --- AiProvider ---

    @Test
    fun `AiProvider has correct model IDs`() {
        assertEquals("claude-haiku-4-5-20251001", AiProvider.CLAUDE_HAIKU.modelId)
        assertEquals("claude-sonnet-4-6", AiProvider.CLAUDE_SONNET.modelId)
        assertEquals("gemini-2.0-flash", AiProvider.GEMINI_FLASH.modelId)
    }

    @Test
    fun `AiProvider has display names`() {
        AiProvider.entries.forEach { provider ->
            assertTrue(provider.displayName.isNotBlank())
        }
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
}
