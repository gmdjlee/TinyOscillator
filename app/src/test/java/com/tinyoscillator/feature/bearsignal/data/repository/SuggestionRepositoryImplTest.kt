package com.tinyoscillator.feature.bearsignal.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionGroupOutcome
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SuggestionRepositoryImpl] 테스트 — Claude API 키/제공자 검증(§4.5 "Anthropic 전용", "키 미설정
 * 시 안내") + 성공 시 [LlmMarketDataSource] 위임.
 */
class SuggestionRepositoryImplTest {

    private val llmMarketDataSource = mockk<LlmMarketDataSource>()
    private val apiConfigProvider = mockk<ApiConfigProvider>()
    private val repository = SuggestionRepositoryImpl(llmMarketDataSource, apiConfigProvider)

    private val current = BearSignalReportBaseline.toInputs()

    private fun emptyFetchResult() = SuggestionFetchResult(
        rateDir = SuggestionGroupOutcome(emptyList(), null),
        bigDealLossRatio = SuggestionGroupOutcome(emptyList(), null),
        credit = SuggestionGroupOutcome(emptyList(), null)
    )

    @Test
    fun `Claude 키 미설정이면 네트워크 호출 없이 failure를 반환한다`() = runTest {
        coEvery { apiConfigProvider.getAiConfig() } returns AiApiKeyConfig(AiProvider.CLAUDE, apiKey = "", modelId = "")

        val result = repository.fetchSuggestions(current)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiError.NoApiKeyError)
        coVerify(exactly = 0) { llmMarketDataSource.fetchSuggestions(any(), any()) }
    }

    @Test
    fun `Gemini가 선택돼 있으면 Anthropic 전용이므로 failure를 반환한다`() = runTest {
        coEvery { apiConfigProvider.getAiConfig() } returns AiApiKeyConfig(
            AiProvider.GEMINI, apiKey = "gemini-key", modelId = "gemini-2.0-flash"
        )

        val result = repository.fetchSuggestions(current)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { llmMarketDataSource.fetchSuggestions(any(), any()) }
    }

    @Test
    fun `Claude 키가 유효하면 LlmMarketDataSource에 위임한다`() = runTest {
        val config = AiApiKeyConfig(AiProvider.CLAUDE, apiKey = "sk-ant-test", modelId = "claude-3-5-haiku-latest")
        coEvery { apiConfigProvider.getAiConfig() } returns config
        coEvery { llmMarketDataSource.fetchSuggestions(config, current) } returns emptyFetchResult()

        val result = repository.fetchSuggestions(current)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { llmMarketDataSource.fetchSuggestions(config, current) }
    }

    @Test
    fun `LlmMarketDataSource가 예외를 던지면 ApiError로 매핑된 failure를 반환한다`() = runTest {
        val config = AiApiKeyConfig(AiProvider.CLAUDE, apiKey = "sk-ant-test", modelId = "claude-3-5-haiku-latest")
        coEvery { apiConfigProvider.getAiConfig() } returns config
        coEvery { llmMarketDataSource.fetchSuggestions(config, current) } throws RuntimeException("boom")

        val result = repository.fetchSuggestions(current)

        assertTrue(result.isFailure)
    }
}
