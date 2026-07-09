package com.tinyoscillator.feature.bearsignal.domain.model

/**
 * 수동 입력([C]/[D] 등급) 도메인 모델 — TASK.md §1.2 하이브리드 데이터 아키텍처, Phase 3.
 *
 * 자동 수집이 불가능하거나(반자동/이벤트성) 데이터가 아예 없는 지표를 사용자가 직접 입력한다.
 * 값은 [AutoIndicator] 래퍼를 재사용해 `source`(항상 [InputSource.MANUAL])·`updatedAt`을 부착한다.
 */

/**
 * 수동 오버라이드 캐시 키 — [com.tinyoscillator.feature.bearsignal.data.local.BearSignalManualInputEntity]의
 * primary key로도 사용한다. [BearIndicatorKey]([A]/[B] 자동 지표)와는 별도 테이블·별도 키 공간이다.
 */
enum class ManualIndicatorKey(val key: String) {
    /** §3.3 신호3 — 적자 상장 비중(%). 리포트 스냅샷 기본값 [BearSignalReportBaseline.LOSS] */
    LOSS("loss"),

    /** §3.3 신호3 — 대어(OpenAI·Anthropic 등) 공모 소화. smooth/pending/failed ([IpoBigConsumption]) */
    BIG("big"),

    /**
     * §1.1 "KR 상장사 적자·신주 발행 비중" — 신주 비중(%). **모니터링 전용** — §3 스코어링
     * ([scoreS3][com.tinyoscillator.feature.bearsignal.domain.usecase.ComputeBearSignalUseCase.Companion.scoreS3])은
     * `loss`만 사용하며 이 값은 사용하지 않는다(SSOT 불변).
     */
    ISSUE_RATIO("issue_ratio"),

    /** §3.4 금리 방아쇠 — 신용거래융자 잔고(조원). v1 수동([C], KRX 정보데이터시스템/KOFIA 배치는 v2) */
    CREDIT("credit"),

    /** §3.4 금리 방아쇠 — 반대매매 임박(담보유지 140% 근접) 여부. [D] 등급(집계 미공개) */
    MARGIN("margin"),

    /**
     * §3.4 금리 방아쇠 — 정책 방향 수동 오버라이드. ease/hold/hike. Phase 2에서 ECOS로 자동
     * 수집되지만(`GATE_DIR`), §4 "폴백" 열이 명시하듯 수동 오버라이드가 항상 우선한다(MANUAL 우선).
     */
    DIR("dir");

    companion object {
        fun fromKey(key: String): ManualIndicatorKey? = entries.find { it.key == key }
    }
}

/** §3.3 신호3 `big`(대어 공모 소화) 값 상수 — 부록A `scoreS3` 문자열과 동일해야 한다(SSOT). */
object IpoBigConsumption {
    const val SMOOTH = "smooth"
    const val PENDING = "pending"
    const val FAILED = "failed"
    val VALID: Set<String> = setOf(SMOOTH, PENDING, FAILED)
}

/**
 * 수동 오버라이드 결과 — Phase 3 [C]/[D] 등급 6개 필드.
 *
 * 필드가 `null`이면 아직 수동 입력이 없다는 뜻이며, 이 경우 병합
 * ([com.tinyoscillator.feature.bearsignal.domain.usecase.MergeBearSignalInputsUseCase])은 AUTO 값,
 * 그마저 없으면 리포트 기준값([BearSignalReportBaseline])으로 폴백한다.
 *
 * [issueRatio]는 §3 스코어링에 사용되지 않는 모니터링 전용 필드다(§1.1 각주3).
 */
data class ManualBearSignalInputs(
    val loss: AutoIndicator<Double>? = null,
    val big: AutoIndicator<String>? = null,
    val issueRatio: AutoIndicator<Double>? = null,
    val credit: AutoIndicator<Double>? = null,
    val margin: AutoIndicator<Boolean>? = null,
    val dir: AutoIndicator<String>? = null
)

/**
 * 국가별 지수 수익률 수동 오버라이드 한 행 (§4 "해외 19개 지수" 폴백, §5.3 인라인 편집).
 *
 * @param r [−12M, −6M, −3M, −1M] 누적수익률(%). 기간별로 null이면 그 기간은 미입력 — 병합 시
 * AUTO/리포트 기준값으로 폴백한다(기간 단위 병합, 전체 행 단위가 아님).
 */
data class ManualMarketReturn(
    val name: String,
    val r: List<Double?>,
    val updatedAt: Long
)

/**
 * BottomSheet(Stepper/SegmentedButton/Slider) 입력 → [com.tinyoscillator.feature.bearsignal.domain.usecase.UpdateManualInputUseCase]
 * 전달용 갱신 요청. 각 하위 타입 값의 문자열 규약은 §3 스코어링 입력과 동일하다.
 */
sealed interface ManualFieldUpdate {
    data class Loss(val value: Double) : ManualFieldUpdate
    data class Big(val value: String) : ManualFieldUpdate
    data class IssueRatio(val value: Double) : ManualFieldUpdate
    data class Credit(val value: Double) : ManualFieldUpdate
    data class Margin(val value: Boolean) : ManualFieldUpdate
    data class Dir(val value: String) : ManualFieldUpdate

    /** [name]은 [BearSignalReportBaseline.MARKETS] 표기와 동일해야 병합 시 매칭된다(예: "RTS", "코스피"). */
    data class MarketReturn(val name: String, val r: List<Double?>) : ManualFieldUpdate
}
