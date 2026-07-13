package com.tinyoscillator.feature.bearsignal.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionFetchResult
import com.tinyoscillator.feature.bearsignal.domain.repository.SuggestionRepository
import kotlinx.coroutines.CancellationException

/**
 * [SuggestionRepository] 구현 — 기존 [ApiConfigProvider] 자격증명 패턴을 재사용해 Claude API 키를
 * 조회한 뒤 [LlmMarketDataSource]에 위임한다(TASK_bear_signal_console.md §4.5 항목4).
 *
 * §4.5는 Anthropic 전용이다(Gemini는 이번 범위 아님) — 사용자가 Gemini를 선택했거나 Claude 키가
 * 미설정이면 네트워크 호출 자체를 시도하지 않고 [Result.failure]로 안내한다(패널이 크래시 없이
 * 안내 문구를 표시할 수 있도록).
 */
class SuggestionRepositoryImpl(
    private val llmMarketDataSource: LlmMarketDataSource,
    private val apiConfigProvider: ApiConfigProvider
) : SuggestionRepository {

    override suspend fun fetchSuggestions(current: BearSignalInputs): Result<SuggestionFetchResult> {
        val config = apiConfigProvider.getAiConfig()
        if (config.provider != AiProvider.CLAUDE || !config.isValid()) {
            return Result.failure(
                ApiError.NoApiKeyError(
                    "AI 제안을 가져오려면 설정 > API에서 Claude API 키를 등록해야 합니다(§4.5는 Anthropic 전용)."
                )
            )
        }
        return try {
            Result.success(llmMarketDataSource.fetchSuggestions(config, current))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ApiError.mapException(e))
        }
    }
}
