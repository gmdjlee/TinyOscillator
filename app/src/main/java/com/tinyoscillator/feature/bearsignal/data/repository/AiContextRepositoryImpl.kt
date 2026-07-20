package com.tinyoscillator.feature.bearsignal.data.repository

import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.config.ApiConfigProvider
import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextDao
import com.tinyoscillator.feature.bearsignal.data.mapper.AiContextClaimMapper
import com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextFetchResult
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.ApprovedAiContext
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

    /**
     * §4.7 개별 클레임 ✓ 승인 — PK가 섹션 단위 REPLACE(upsert)이므로 전달된 클레임만 저장하면
     * 같은 섹션에서 클레임을 하나씩 승인할 때 마지막 1건만 생존한다(TASK_code_review_improvements.md
     * P1a-4). 따라서 upsert 전에 기존 승인분을 read → 신규 클레임과 병합 → 다시 upsert한다.
     *
     * 병합 규칙: 같은 [AiContextClaim.text]는 신규 클레임이 기존(과거 승인)을 대체한다
     * (`distinctBy` 첫 항목 유지 — `sectionClaims + existing` 순서로 신규를 앞에 둠) — 같은 클레임을
     * 재승인해도 결과가 1건으로 수렴하는 멱등성을 보장한다. asOf는 병합된 전체 클레임의
     * source_date 최댓값(§4.7 "여러 클레임이 섞여 있으면 최신값을 저장"). fetch는 여전히 어떤 Room
     * 쓰기도 하지 않는다 — 이 read는 approve 내부 병합 전용이며 §4.7 "fetch 무저장" 불변을 깨지 않는다.
     */
    override suspend fun approve(claims: List<AiContextClaim>, provider: String, now: Long) {
        claims.groupBy { it.sectionKey }.forEach { (sectionKey, sectionClaims) ->
            val existing = dao.getBySectionKey(sectionKey.key)
                ?.let { AiContextClaimMapper.toDomain(it) }
                .orEmpty()
            val merged = (sectionClaims + existing).distinctBy { it.text }
            val asOf = merged.maxOf { it.sourceDate }
            dao.upsert(AiContextClaimMapper.toEntity(sectionKey, merged, provider, asOf, now))
        }
    }

    override suspend fun getApproved(): Map<AiContextSectionKey, ApprovedAiContext> =
        dao.getAll()
            .mapNotNull { entity ->
                val sectionKey = AiContextSectionKey.fromKey(entity.sectionKey) ?: return@mapNotNull null
                // §4.7 "캐시 없으면 정적 fallback 그대로" — content_json 파싱이 전멸(0건)하면 승인
                // 캐시가 아예 없는 것과 동일하게 취급해 맵에서 생략한다(렌더가 정적 fallback으로 대체).
                val claims = AiContextClaimMapper.toDomain(entity).ifEmpty { return@mapNotNull null }
                sectionKey to ApprovedAiContext(
                    claims = claims,
                    provider = entity.provider,
                    asOf = entity.asOf,
                    approvedAt = entity.approvedAt
                )
            }
            .toMap()
}
