package com.tinyoscillator.feature.bearsignal.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextDao
import com.tinyoscillator.feature.bearsignal.data.mapper.AiContextClaimMapper
import com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.repository.AiContextRepository
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/**
 * [AiContextRepository] 구현 — §4.5 [SuggestionRepositoryImpl]과 동일한 자격증명 검증 패턴을
 * 재사용해 설정의 AI 제공자(Claude 또는 Gemini) 키를 조회한 뒤 [LlmMarketDataSource]에 위임한다
 * (TASK_bear_signal_console.md §4.7, Phase 7-2).
 *
 * [fetchUpdates]는 어떤 Room 쓰기도 수행하지 않는다 — 저장은 [approve]가 사용자의 명시적 승인
 * 이후에만 [BearSignalAiContextDao.upsert]를 호출한다(§4.7 "승인 없이는 표시 콘텐츠 불변").
 */
class AiContextRepositoryImpl(
    private val llmMarketDataSource: LlmMarketDataSource,
    private val apiConfigProvider: ApiConfigProvider,
    private val dao: BearSignalAiContextDao
) : AiContextRepository {

    override suspend fun fetchUpdates(today: LocalDate): Result<AiContextFetchResult> {
        val config = apiConfigProvider.getAiConfig()
        if (!config.isValid()) {
            return Result.failure(
                ApiError.NoApiKeyError(
                    "정세 업데이트를 가져오려면 설정 > API에서 Claude 또는 Gemini API 키를 등록해야 합니다."
                )
            )
        }
        return try {
            Result.success(llmMarketDataSource.fetchAiContextUpdates(config, today))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ApiError.mapException(e))
        }
    }

    override suspend fun approve(claims: List<AiContextClaim>, provider: String, now: Long) {
        claims.groupBy { it.sectionKey }.forEach { (sectionKey, sectionClaims) ->
            // §4.7 "여러 클레임이 섞여 있으면 최신값을 저장" — 섹션 내 클레임들의 source_date 최댓값.
            val asOf = sectionClaims.maxOf { it.sourceDate }
            dao.upsert(AiContextClaimMapper.toEntity(sectionKey, sectionClaims, provider, asOf, now))
        }
    }

    override suspend fun getApproved(): Map<AiContextSectionKey, List<AiContextClaim>> =
        dao.getAll()
            .mapNotNull { entity ->
                AiContextSectionKey.fromKey(entity.sectionKey)?.let { it to AiContextClaimMapper.toDomain(entity) }
            }
            .toMap()
}
