package com.tinyoscillator.feature.bearsignal.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * §4.5 제안 신선도(STALE) 허용 연령 상수 — §3 스코어링 임계치(`bear_thresholds.json`)가 **아니다**.
 * 이 제안이 STALE로 표시되는 UI/수집 파라미터일 뿐이며, 값이 바뀌어도 §3 판정 로직에는 영향이 없다.
 *
 * Kotlin enum 제약(생성자 인자는 자신의 companion object를 참조할 수 없음)으로 인해 [SuggestionField]
 * 바깥의 top-level 상수로 선언한다.
 *
 * 미 연준 FOMC 정례회의 주기(약 6주)에 여유를 더한 값 — 공식 발표 사이 정상적인 "안 바뀜"과 실제로
 * 낡은 값을 구분하기 위한 UI 파라미터(§3 임계치 아님).
 */
private const val RATE_DIR_MAX_AGE_DAYS = 45L

/** KOFIA 신용거래융자 주간 통계 발표 주기(7일) + 며칠 여유. */
private const val CREDIT_MAX_AGE_DAYS = 10L

/** 이벤트성 정성 지표(대어 소화·적자상장비중)는 뉴스 갱신 빈도가 낮아 더 넉넉히 허용한다. */
private const val EVENT_MAX_AGE_DAYS = 30L

/**
 * §4.5 웹/LLM 3-tier 수집 · 승인 흐름의 제안 필드 (TASK_bear_signal_console.md §4.5, Phase 4).
 *
 * 각 필드는 [BearSignalAutoCacheEntity]/[BearIndicatorKey] 승인 반영 경로로 직결된다 — 승인 시
 * `source=AUTO`로 [indicatorKey]에 upsert된다(§4.6 "§4.5 웹 수집의 WEB 출처는 AUTO에 속한다").
 *
 * [maxAgeDays]는 §3 스코어링 임계치(`bear_thresholds.json`)가 **아니다** — 이 제안이 STALE로
 * 표시되는 UI/수집 파라미터일 뿐이며, 값이 바뀌어도 §3 판정 로직에는 영향이 없다.
 */
enum class SuggestionField(
    val indicatorKey: BearIndicatorKey,
    val labelKo: String,
    val maxAgeDays: Long
) {
    /** 미 연준 목표금리 상단(%) — §3.4 `scoreGate`의 `rate` 입력 후보 */
    RATE(BearIndicatorKey.GATE_RATE, "미 연준 목표금리 상단(%)", RATE_DIR_MAX_AGE_DAYS),

    /** 정책 방향(ease/hold/hike) — §3.4 `scoreGate`의 `dir` 입력 후보 */
    DIR(BearIndicatorKey.GATE_DIR, "정책 방향", RATE_DIR_MAX_AGE_DAYS),

    /** 대어(OpenAI·Anthropic 등) 공모 소화(smooth/pending/failed) — §3.3 `scoreS3`의 `big` 입력 후보 */
    BIG_DEAL(BearIndicatorKey.S3_BIG_DEAL, "대어 IPO 소화", EVENT_MAX_AGE_DAYS),

    /** 적자상장비중(%) — §3.3 `scoreS3`의 `loss` 입력 후보 */
    LOSS_RATIO(BearIndicatorKey.S3_LOSS_RATIO, "적자상장비중(%)", EVENT_MAX_AGE_DAYS),

    /** 신용거래융자 잔고(조원) — §3.4 `scoreGate`의 `credit` 입력 후보 */
    CREDIT(BearIndicatorKey.GATE_CREDIT, "신용거래융자 잔고(조원)", CREDIT_MAX_AGE_DAYS)
}

/**
 * §4.5 제안 한 건 — `field/current/next/as_of/origin/stale` (TASK_bear_signal_console.md §4.5 항목2).
 *
 * @param currentValue 승인 전 병합된 현재 값(표시용, 포맷된 문자열). 알 수 없으면 null.
 * @param nextValue LLM이 제안한 값 — 이미 [SuggestionValidation] 화이트리스트 검증을 통과한 값만
 * 이 필드에 담긴다(위반 필드는 애초에 [Suggestion]으로 만들어지지 않는다).
 * @param stale [SuggestionField.maxAgeDays] 초과 시 true — UI가 STALE 배지로 표시한다.
 */
data class Suggestion(
    val field: SuggestionField,
    val currentValue: String?,
    val nextValue: String,
    val asOf: LocalDate,
    val origin: String,
    val stale: Boolean
)

/**
 * 제안 그룹(§4.5 그룹①②③) 하나의 수집 결과 — 부분 실패 격리(그룹 실패는 다른 그룹에 영향 없음).
 *
 * @param searchWidgetHtml §4.5 v1.3 "Gemini 경로" — `groundingMetadata.searchEntryPoint.renderedContent`
 * (Google 검색 제안 위젯 HTML, ToS상 사용자 표시 의무). Claude 경로는 항상 null. 급변 재확인 호출의
 * widget은 무시하고 최초 호출 결과만 담는다.
 */
data class SuggestionGroupOutcome(
    val suggestions: List<Suggestion>,
    val error: String?,
    val searchWidgetHtml: String? = null
)

/** [com.tinyoscillator.feature.bearsignal.domain.repository.SuggestionRepository.fetchSuggestions] 결과. */
data class SuggestionFetchResult(
    val rateDir: SuggestionGroupOutcome,
    val bigDealLossRatio: SuggestionGroupOutcome,
    val credit: SuggestionGroupOutcome
) {
    val all: List<Suggestion> get() = rateDir.suggestions + bigDealLossRatio.suggestions + credit.suggestions
    val failedGroupMessages: List<String> get() = listOfNotNull(rateDir.error, bigDealLossRatio.error, credit.error)

    /** §4.5 v1.3 "Gemini 경로" — 그룹별 검색 제안 위젯 HTML을 중복 제거해 모은다(`SuggestionPanel` 표시용). */
    val searchWidgetsHtml: List<String>
        get() = listOfNotNull(rateDir.searchWidgetHtml, bigDealLossRatio.searchWidgetHtml, credit.searchWidgetHtml)
            .distinct()
}

/**
 * §4.5 제안 검증 순수 함수 모음 — 안드로이드/네트워크 의존성 0(JVM 단위테스트 대상).
 *
 * [RATE_VOLATILITY_THRESHOLD]/[CREDIT_VOLATILITY_RATIO]는 §3 임계치가 아니라 §4.5가 명시한
 * "급변 재확인" 트리거 파라미터다 — 초과 시 [com.tinyoscillator.feature.bearsignal.data.remote.LlmMarketDataSource]가
 * 동일 그룹을 1회 재호출해 두 결과가 일치할 때만 제안 목록에 올린다.
 */
object SuggestionValidation {
    private val VALID_DIR = setOf("ease", "hold", "hike")

    /** §3.4 `dir` 값 상수(`hike`/`hold`/`ease`)와 동일해야 한다(SSOT — `scoreGate` 문자열 규약 재사용). */
    fun isValidDir(value: String): Boolean = value in VALID_DIR

    /** §3.3 `big` 값 상수([IpoBigConsumption.VALID])와 동일해야 한다. */
    fun isValidBigDeal(value: String): Boolean = value in IpoBigConsumption.VALID

    /** [asOf]가 [today] 기준 [maxAgeDays]를 초과하면 STALE. */
    fun isStale(asOf: LocalDate, today: LocalDate, maxAgeDays: Long): Boolean =
        ChronoUnit.DAYS.between(asOf, today) > maxAgeDays

    /** §4.5 "금리 ±0.5%p 초과" 급변 판정 — [current]가 없으면(기준 없음) 재확인이 무의미하므로 false. */
    fun isVolatileRateChange(current: Double?, proposed: Double): Boolean =
        current != null && abs(proposed - current) > RATE_VOLATILITY_THRESHOLD

    /** §4.5 "신용잔고 ±30% 초과" 급변 판정 — [current]가 0이면 비율 계산이 무의미하므로 false. */
    fun isVolatileCreditChange(current: Double?, proposed: Double): Boolean =
        current != null && current != 0.0 && abs(proposed - current) / abs(current) > CREDIT_VOLATILITY_RATIO

    /** §4.5 "금리 ±0.5%p 초과" 급변 재확인 트리거 임계값(%p) — §3 임계치 아님. */
    const val RATE_VOLATILITY_THRESHOLD = 0.5

    /** §4.5 "신용잔고 ±30% 초과" 급변 재확인 트리거 임계값(비율) — §3 임계치 아님. */
    const val CREDIT_VOLATILITY_RATIO = 0.30
}
