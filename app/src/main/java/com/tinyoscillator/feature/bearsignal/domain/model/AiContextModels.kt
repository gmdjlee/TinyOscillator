package com.tinyoscillator.feature.bearsignal.domain.model

import java.time.LocalDate

/**
 * §4.7 클레임 신선도(STALE) 허용 연령 상수 — §3 스코어링 임계치(`bear_thresholds.json`)가 **아니다**.
 * [SuggestionField]의 `maxAgeDays` 관례와 동일한 UI/수집 파라미터일 뿐이며, 값이 바뀌어도 §3 판정
 * 로직에는 영향이 없다. Kotlin enum 제약(생성자 인자는 자신의 companion object를 참조할 수 없음)으로
 * 인해 [AiContextSectionKey] 바깥의 top-level 상수로 선언한다.
 */
private const val MONITOR_MAX_AGE_DAYS = 45L

/** 사례(cases)·역사 검증 "현재 비교" 문단은 사건성 갱신 빈도가 낮아 더 넉넉히 허용한다. */
private const val CASES_OR_HISTORY_MAX_AGE_DAYS = 30L

/**
 * §4.7 갱신 대상 섹션(TASK_bear_signal_console.md §4.7 "갱신 대상" 표) — 프롬프트-JSON `section_key`
 * 값과 1:1 대응([key]).
 *
 * [allowInterpretation]이 false인 섹션(`monitor`/`cases`)은 `type=fact` 클레임만 허용 —
 * `interpretation`이면 [AiContextClaimValidation]이 폐기한다. `history_current`만 `interpretation`을
 * 허용하되 UI가 "AI 견해" 배지를 병기한다(P7-3 몫).
 *
 * [maxAgeDays]는 §3 임계치가 **아니다** — STALE 배지 판정에만 쓰이는 UI 파라미터.
 */
enum class AiContextSectionKey(
    val key: String,
    val allowInterpretation: Boolean,
    val maxAgeDays: Long
) {
    TYPE0_MONITOR("type0_monitor", allowInterpretation = false, maxAgeDays = MONITOR_MAX_AGE_DAYS),
    TYPE1_MONITOR("type1_monitor", allowInterpretation = false, maxAgeDays = MONITOR_MAX_AGE_DAYS),
    TYPE2_MONITOR("type2_monitor", allowInterpretation = false, maxAgeDays = MONITOR_MAX_AGE_DAYS),
    TYPE0_CASES("type0_cases", allowInterpretation = false, maxAgeDays = CASES_OR_HISTORY_MAX_AGE_DAYS),
    TYPE1_CASES("type1_cases", allowInterpretation = false, maxAgeDays = CASES_OR_HISTORY_MAX_AGE_DAYS),
    TYPE2_CASES("type2_cases", allowInterpretation = false, maxAgeDays = CASES_OR_HISTORY_MAX_AGE_DAYS),
    HISTORY_CURRENT("history_current", allowInterpretation = true, maxAgeDays = CASES_OR_HISTORY_MAX_AGE_DAYS);

    companion object {
        /** 프롬프트-JSON `section_key` 문자열 → enum. 알 수 없는 키는 null(호출측에서 클레임 폐기 처리). */
        fun fromKey(key: String): AiContextSectionKey? = entries.find { it.key == key }
    }
}

/** §4.7 클레임 스키마 `type` 필드(`fact` | `interpretation`). */
enum class ClaimType(val key: String) {
    FACT("fact"),
    INTERPRETATION("interpretation");

    companion object {
        fun fromKey(key: String): ClaimType? = entries.find { it.key == key }
    }
}

/**
 * §4.7 클레임 원시 파싱 결과(검증 전) — LLM 응답 `claims[]` 항목을 관용 파싱한 그대로 담는다.
 * P7-2의 파싱 계층이 채우며, 이 모델 자체는 검증 순수함수([AiContextClaimValidation])의 테스트
 * 가능한 입력 형태로서 안드로이드/네트워크 의존성이 없다.
 *
 * 필드가 파싱 실패·누락되면 null로 둔다 — 검증 단계에서 폐기 사유로 취급한다
 * ([sourceUrl] 부재 → [AiContextClaimRejection.URL_NOT_VERIFIED], [sourceDate] 부재 →
 * [AiContextClaimRejection.SOURCE_DATE_MISSING]).
 */
data class AiContextClaimDraft(
    val sectionKey: AiContextSectionKey,
    val text: String,
    val type: ClaimType,
    val sourceUrl: String?,
    val sourceTitle: String?,
    val sourceDate: LocalDate?,
    val quote: String?
)

/**
 * §4.7 검증 통과 클레임 — [BearSignalAiContextEntity][com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextEntity]
 * `content_json`에 배열로 직렬화되어 저장된다(§4.7 "저장" 절, "quote 동봉 — 출처 링크 사망 후에도
 * 증거 보존").
 */
data class AiContextClaim(
    val sectionKey: AiContextSectionKey,
    val text: String,
    val type: ClaimType,
    val sourceUrl: String,
    val sourceTitle: String,
    val sourceDate: LocalDate,
    val quote: String?
)

/** [AiContextClaimValidation.validate]가 클레임을 폐기한 사유(§4.7 "검증 파이프라인" 1~3항). */
enum class AiContextClaimRejection {
    /** §4.7 검증1: `source_url`이 동봉된 실제 검색결과 URL 목록에 없음(환각 URL 차단). */
    URL_NOT_VERIFIED,

    /** §4.7 검증2: `source_date` 부재. */
    SOURCE_DATE_MISSING,

    /** §4.7 "monitor·cases 클레임은 type=fact만 허용" — 위반(interpretation). */
    INTERPRETATION_NOT_ALLOWED,

    /** §4.7 검증3: `fact` 클레임의 `quote` 부재. */
    FACT_QUOTE_MISSING
}

/** [AiContextClaimValidation.validate] 결과 — 클레임 단위 통과/폐기(§4.7 "그룹 폐기 아님"). */
sealed class AiContextClaimValidationResult {
    /** @param stale [AiContextSectionKey.maxAgeDays] 초과 시 true(폐기 아님 — UI STALE 배지). */
    data class Accepted(val claim: AiContextClaim, val stale: Boolean) : AiContextClaimValidationResult()

    data class Rejected(val reason: AiContextClaimRejection) : AiContextClaimValidationResult()
}

/**
 * §4.7 클레임 검증 파이프라인 순수 함수 모음 — 안드로이드/네트워크 의존성 0(JVM 단위테스트 대상).
 *
 * TASK_bear_signal_console.md §4.7 "검증 파이프라인"(라인 350~355)을 그대로 구현한다. 위반 시
 * **클레임 단위 폐기**이며 그룹(섹션) 전체 폐기가 아니다 — 호출측(P7-2)은 클레임마다 개별 호출한다.
 */
object AiContextClaimValidation {

    /**
     * §4.7 검증1 "URL 교차검증": [sourceUrl]이 동봉된 실제 검색결과 URL 목록([resultUrls])에 없으면
     * false(환각/조작 URL 차단). 트레일링 슬래시·스킴 대소문자 차이로 인한 오탐을 줄이기 위해
     * 정규화 후 비교한다(과도한 정규화는 하지 않음 — 스펙은 "목록에 없으면 폐기"만 요구).
     */
    fun isUrlVerified(sourceUrl: String, resultUrls: Collection<String>): Boolean {
        val normalized = normalizeUrl(sourceUrl)
        return resultUrls.any { normalizeUrl(it) == normalized }
    }

    private fun normalizeUrl(url: String): String = url.trim().trimEnd('/').lowercase()

    /** §4.7 검증2 STALE 판정 — [SuggestionValidation.isStale]과 동일 계산([sectionKey.maxAgeDays] 재사용). */
    fun isStale(sourceDate: LocalDate, today: LocalDate, sectionKey: AiContextSectionKey): Boolean =
        SuggestionValidation.isStale(sourceDate, today, sectionKey.maxAgeDays)

    /**
     * §4.7 검증 파이프라인 전체 실행. 순서: URL 교차검증 → source_date 부재 → 섹션별 type 제약
     * (interpretation 허용 여부) → fact quote 부재. 어느 하나라도 위반하면 해당 사유로 즉시 폐기하고,
     * 전부 통과하면 [AiContextClaim] + STALE 플래그를 담아 반환한다.
     */
    fun validate(
        draft: AiContextClaimDraft,
        resultUrls: Collection<String>,
        today: LocalDate
    ): AiContextClaimValidationResult {
        val sourceUrl = draft.sourceUrl
        if (sourceUrl == null || !isUrlVerified(sourceUrl, resultUrls)) {
            return AiContextClaimValidationResult.Rejected(AiContextClaimRejection.URL_NOT_VERIFIED)
        }

        val sourceDate = draft.sourceDate
            ?: return AiContextClaimValidationResult.Rejected(AiContextClaimRejection.SOURCE_DATE_MISSING)

        if (!draft.sectionKey.allowInterpretation && draft.type == ClaimType.INTERPRETATION) {
            return AiContextClaimValidationResult.Rejected(AiContextClaimRejection.INTERPRETATION_NOT_ALLOWED)
        }

        if (draft.type == ClaimType.FACT && draft.quote.isNullOrBlank()) {
            return AiContextClaimValidationResult.Rejected(AiContextClaimRejection.FACT_QUOTE_MISSING)
        }

        val claim = AiContextClaim(
            sectionKey = draft.sectionKey,
            text = draft.text,
            type = draft.type,
            sourceUrl = sourceUrl,
            sourceTitle = draft.sourceTitle.orEmpty(),
            sourceDate = sourceDate,
            quote = draft.quote
        )
        return AiContextClaimValidationResult.Accepted(claim, isStale(sourceDate, today, draft.sectionKey))
    }
}

/**
 * §4.7 그룹(④monitor/⑤cases/⑥history_current) 단일 그룹 조회 결과 (Phase 7-2) —
 * [com.tinyoscillator.feature.bearsignal.domain.model.SuggestionGroupOutcome](§4.5)과 동일한 부분
 * 실패 격리 패턴을 재사용한다. 이 시점에는 아직 Room에 저장되지 않는다(§4.7 "승인 없이는 표시
 * 콘텐츠 불변") — 저장은
 * [com.tinyoscillator.feature.bearsignal.data.repository.AiContextRepositoryImpl.approve]가 사용자
 * 승인 후에만 수행한다.
 *
 * @param pending 검증 통과 클레임(STALE 포함, [AiContextClaimValidationResult.Accepted]) — 사용자
 * 승인 대기 목록. 이 함수 자체는 어떤 것도 승인하지 않는다.
 * @param rejectedCounts 폐기 사유별 건수([AiContextClaimRejection]) — 그룹 폐기가 아니라 클레임 단위
 * 폐기이므로 그룹 자체는 여전히 `error == null`일 수 있다(클레임이 전부 폐기돼도 호출 자체는 성공).
 * @param error 그룹 호출 자체 실패 메시지(네트워크·응답 파싱 실패) — null이면 호출은 성공했다는 뜻
 * (단, [pending]이 0건일 수 있다).
 * @param searchWidgetHtml §4.5/§4.7 Gemini 경로 검색 제안 위젯 HTML(ToS 표시 의무, §4.5 v1.3 계승).
 * @param provider 이 그룹 조회에 사용된 제공자("claude"|"gemini",
 * [com.tinyoscillator.feature.bearsignal.data.local.BearSignalAiContextEntity.provider] 규약과 동일)
 * — 승인 시 [com.tinyoscillator.feature.bearsignal.domain.repository.AiContextRepository.approve]에 전달돼 저장된다.
 */
data class AiContextGroupOutcome(
    val pending: List<AiContextClaimValidationResult.Accepted>,
    val rejectedCounts: Map<AiContextClaimRejection, Int>,
    val error: String?,
    val searchWidgetHtml: String?,
    val provider: String?
)

/** [com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource.fetchAiContextUpdates] 결과(§4.7 그룹④⑤⑥ 통합). */
data class AiContextFetchResult(
    val monitor: AiContextGroupOutcome,
    val cases: AiContextGroupOutcome,
    val historyCurrent: AiContextGroupOutcome
) {
    /** 3개 그룹의 승인 대기 클레임 전체(섹션 무관 평탄화) — 승인 미리보기(P7-3)가 그룹핑은 별도로 수행. */
    val allPending: List<AiContextClaimValidationResult.Accepted>
        get() = monitor.pending + cases.pending + historyCurrent.pending

    val failedGroupMessages: List<String>
        get() = listOfNotNull(monitor.error, cases.error, historyCurrent.error)

    /** §4.5 v1.3 "Gemini 경로" 검색 제안 위젯 — 그룹별 HTML을 중복 제거해 모은다. */
    val searchWidgetsHtml: List<String>
        get() = listOfNotNull(monitor.searchWidgetHtml, cases.searchWidgetHtml, historyCurrent.searchWidgetHtml).distinct()
}
