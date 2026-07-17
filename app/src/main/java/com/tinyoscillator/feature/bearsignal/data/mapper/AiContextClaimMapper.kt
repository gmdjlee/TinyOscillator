package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaim
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextClaimPayload
import com.tinyoscillator.feature.bearsignal.domain.model.AiContextSectionKey
import com.tinyoscillator.feature.bearsignal.domain.model.ClaimType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * [AiContextClaim] ↔ [BearSignalAiContextEntity] 변환 (data 계층, §4.7 Phase 7-2).
 *
 * [BearSnapshotMapper]와 달리 `content_json`이 [AiContextClaim] **목록**을 담으므로, 도메인 객체
 * 리스트 ↔ JSON 배열 직렬화까지 이 매퍼가 책임진다([AiContextClaimPayload] 경유, `LocalDate` ↔
 * ISO-8601 문자열 변환 포함).
 */
object AiContextClaimMapper {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(AiContextClaimPayload.serializer())

    /**
     * 한 섹션의 승인 클레임 목록 → entity. [asOf]/[provider]/[approvedAt]은 호출측
     * ([com.tinyoscillator.feature.bearsignal.data.repository.AiContextRepositoryImpl.approve])이
     * 산정해 전달한다(§4.7 "여러 클레임이 섞여 있으면 최신값을 저장").
     */
    fun toEntity(
        sectionKey: AiContextSectionKey,
        claims: List<AiContextClaim>,
        provider: String,
        asOf: LocalDate,
        approvedAt: Long
    ): BearSignalAiContextEntity = BearSignalAiContextEntity(
        sectionKey = sectionKey.key,
        contentJson = json.encodeToString(listSerializer, claims.map { it.toPayload() }),
        asOf = asOf.toString(),
        provider = provider,
        approvedAt = approvedAt
    )

    /**
     * entity → 도메인 클레임 목록. `content_json`이 손상됐거나(파싱 실패) 개별 항목의
     * `section_key`/`type`/`source_date`가 손상돼 있으면 그 항목만(또는 전체가 파싱 불가하면 전부)
     * 건너뛴다 — 렌더 계층(P7-3)이 크래시 없이 정적 fallback으로 대체할 수 있도록 방어적으로 처리한다.
     */
    fun toDomain(entity: BearSignalAiContextEntity): List<AiContextClaim> {
        val payloads = try {
            json.decodeFromString(listSerializer, entity.contentJson)
        } catch (e: Exception) {
            return emptyList()
        }
        return payloads.mapNotNull { it.toDomainOrNull() }
    }

    private fun AiContextClaim.toPayload(): AiContextClaimPayload = AiContextClaimPayload(
        sectionKey = sectionKey.key,
        text = text,
        type = type.key,
        sourceUrl = sourceUrl,
        sourceTitle = sourceTitle,
        sourceDate = sourceDate.toString(),
        quote = quote
    )

    private fun AiContextClaimPayload.toDomainOrNull(): AiContextClaim? {
        val sectionKeyEnum = AiContextSectionKey.fromKey(sectionKey) ?: return null
        val typeEnum = ClaimType.fromKey(type) ?: return null
        val date = try {
            LocalDate.parse(sourceDate)
        } catch (e: Exception) {
            return null
        }
        return AiContextClaim(
            sectionKey = sectionKeyEnum,
            text = text,
            type = typeEnum,
            sourceUrl = sourceUrl,
            sourceTitle = sourceTitle,
            sourceDate = date,
            quote = quote
        )
    }
}
