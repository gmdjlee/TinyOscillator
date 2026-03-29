package com.tinyoscillator.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** AI 분석 제공자 */
enum class AiProvider(val displayName: String, val modelId: String) {
    CLAUDE_HAIKU("Claude Haiku", "claude-haiku-4-5-20251001"),
    CLAUDE_SONNET("Claude Sonnet", "claude-sonnet-4-6"),
    GEMINI_FLASH("Gemini Flash", "gemini-2.0-flash")
}

/** AI API 키 설정 */
data class AiApiKeyConfig(
    val provider: AiProvider,
    val apiKey: String
) {
    fun isValid(): Boolean = apiKey.isNotBlank()

    fun getBaseUrl(): String = when (provider) {
        AiProvider.CLAUDE_HAIKU, AiProvider.CLAUDE_SONNET ->
            "https://api.anthropic.com"
        AiProvider.GEMINI_FLASH ->
            "https://generativelanguage.googleapis.com"
    }

    override fun toString() = "AiApiKeyConfig(provider=$provider, apiKey=*****)"
}

/** AI 분석 유형 */
enum class AiAnalysisType(val displayName: String) {
    STOCK_OSCILLATOR("종목 수급 분석"),
    ETF_RANKING("ETF 금액 순위 분석"),
    MARKET_OVERVIEW("시장 지표 분석"),
    COMPREHENSIVE_STOCK("종목 종합 분석")
}

/** AI 분석 결과 */
data class AiAnalysisResult(
    val type: AiAnalysisType,
    val provider: AiProvider,
    val content: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/** AI 분석 UI 상태 */
sealed class AiAnalysisState {
    data object Idle : AiAnalysisState()
    data object Loading : AiAnalysisState()
    data class Success(val result: AiAnalysisResult) : AiAnalysisState()
    data class Error(val message: String) : AiAnalysisState()
    data object NoApiKey : AiAnalysisState()
}

// --- Claude Messages API 응답 ---

@Serializable
data class ClaudeResponse(
    val id: String = "",
    val content: List<ClaudeContent> = emptyList(),
    val usage: ClaudeUsage = ClaudeUsage()
)

@Serializable
data class ClaudeContent(
    val type: String = "",
    val text: String = ""
)

@Serializable
data class ClaudeUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0
)

// --- Gemini API 응답 ---

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    val usageMetadata: GeminiUsageMetadata = GeminiUsageMetadata()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent = GeminiContent()
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart> = emptyList()
)

@Serializable
data class GeminiPart(
    val text: String = ""
)

@Serializable
data class GeminiUsageMetadata(
    val promptTokenCount: Int = 0,
    val candidatesTokenCount: Int = 0
)
