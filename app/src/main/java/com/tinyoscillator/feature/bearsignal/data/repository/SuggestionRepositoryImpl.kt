package com.tinyoscillator.feature.bearsignal.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.SuggestionFetchResult
import com.tinyoscillator.feature.bearsignal.domain.repository.SuggestionRepository
import kotlinx.coroutines.CancellationException

/**
 * [SuggestionRepository] 구현 — 기존 [ApiConfigProvider] 자격증명 패턴을 재사용해 설정의 AI 제공자
 * (Claude 또는 Gemini) 키를 조회한 뒤 [LlmMarketDataSource]에 위임한다
 * (TASK_bear_signal_console.md §4.5 항목4, §4.5 v1.3 "제공자 이원화").
 *
 * v1.3부터 §4.5는 Claude/Gemini 두 제공자를 모두 지원한다 — 유효한 API 키가 설정돼 있지 않으면
 * 네트워크 호출 자체를 시도하지 않고 [Result.failure]로 안내한다(패널이 크래시 없이 안내 문구를
 * 표시할 수 있도록).
 */
class SuggestionRepositoryImpl(
    private val llmMarketDataSource: LlmMarketDataSource,
    private val apiConfigProvider: ApiConfigProvider
) : SuggestionRepository {

    override suspend fun fetchSuggestions(current: BearSignalInputs): Result<SuggestionFetchResult> {
        val config = apiConfigProvider.getAiConfig()
        if (!config.isValid()) {
            return Result.failure(
                ApiError.NoApiKeyError(
                    "AI 제안을 가져오려면 설정 > API에서 Claude 또는 Gemini API 키를 등록해야 합니다."
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
