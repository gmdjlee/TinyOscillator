package com.tinyoscillator.presentation.ai

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.domain.model.AiAnalysisResult
import com.tinyoscillator.domain.model.AiAnalysisType
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.domain.model.AiStreamEvent
import com.tinyoscillator.domain.model.ChatMessage
import com.tinyoscillator.domain.model.ChatRole
import org.junit.Assert.*
import org.junit.Test

class AiAnalysisTypesTest {

    private fun msg(role: ChatRole, content: String, ts: Long) = ChatMessage(role, content, ts)

    // --- trimChatHistory ---

    @Test
    fun `상한 이하면 그대로 반환`() {
        val messages = listOf(
            msg(ChatRole.USER, "q1", 1),
            msg(ChatRole.ASSISTANT, "a1", 2)
        )
        assertEquals(messages, trimChatHistory(messages, maxMessages = 12))
    }

    @Test
    fun `상한 초과 시 최근 메시지만 유지`() {
        val messages = (1..20).map { i ->
            msg(if (i % 2 == 1) ChatRole.USER else ChatRole.ASSISTANT, "m$i", i.toLong())
        }
        val trimmed = trimChatHistory(messages, maxMessages = 12)

        assertEquals(12, trimmed.size)
        assertEquals("m9", trimmed.first().content)
        assertEquals(ChatRole.USER, trimmed.first().role)
        assertEquals("m20", trimmed.last().content)
    }

    @Test
    fun `자른 뒤 선두가 assistant면 제거하여 user로 시작`() {
        // 홀수 인덱스가 assistant가 되도록 구성
        val messages = (1..20).map { i ->
            msg(if (i % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT, "m$i", i.toLong())
        }
        val trimmed = trimChatHistory(messages, maxMessages = 12)

        assertEquals(ChatRole.USER, trimmed.first().role)
        assertEquals("m10", trimmed.first().content)
    }

    // --- ChatTokenUsage ---

    @Test
    fun `ChatTokenUsage 누적 합산`() {
        val usage = ChatTokenUsage() +
            AiStreamEvent.Done("a", inputTokens = 100, outputTokens = 50, cacheReadTokens = 80) +
            AiStreamEvent.Done("b", inputTokens = 30, outputTokens = 20)

        assertEquals(130, usage.inputTokens)
        assertEquals(70, usage.outputTokens)
        assertEquals(80, usage.cacheReadTokens)
        assertFalse(usage.isEmpty)
        assertTrue(ChatTokenUsage().isEmpty)
    }

    // --- aiErrorMessage ---

    @Test
    fun `429는 모델 전환 안내 포함`() {
        val message = aiErrorMessage(ApiError.ApiCallError(429, "rate limited"))
        assertTrue(message.contains("한도"))
        assertTrue(message.contains("모델"))
    }

    @Test
    fun `인증 오류는 설정 안내 포함`() {
        val message = aiErrorMessage(ApiError.AuthError("401"))
        assertTrue(message.contains("API 키"))
    }

    // --- 토큰/비용 라벨 ---

    @Test
    fun `formatTokens 천 단위 축약`() {
        assertEquals("999", formatTokens(999))
        assertEquals("1.5k", formatTokens(1500))
    }

    @Test
    fun `estimateCostUsd 알려진 모델만 추정`() {
        val haiku = AiAnalysisResult(
            type = AiAnalysisType.PROBABILITY_INTERPRETATION,
            provider = AiProvider.CLAUDE,
            content = "",
            inputTokens = 1_000_000,
            outputTokens = 0,
            modelId = "claude-3-5-haiku-20241022"
        )
        assertEquals(1.00, estimateCostUsd(haiku)!!, 0.001)

        val unknown = haiku.copy(modelId = "unknown-model")
        assertNull(estimateCostUsd(unknown))
    }

    @Test
    fun `estimateCostUsd 캐시 읽기는 입력 단가의 10퍼센트`() {
        val result = AiAnalysisResult(
            type = AiAnalysisType.PROBABILITY_INTERPRETATION,
            provider = AiProvider.CLAUDE,
            content = "",
            inputTokens = 0,
            outputTokens = 0,
            cacheReadTokens = 1_000_000,
            modelId = "claude-sonnet-4"
        )
        // sonnet 입력 $3.00/M → 캐시 읽기 $0.30/M
        assertEquals(0.30, estimateCostUsd(result)!!, 0.001)
    }
}
