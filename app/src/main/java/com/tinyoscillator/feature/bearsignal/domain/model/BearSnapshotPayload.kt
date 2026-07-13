package com.tinyoscillator.feature.bearsignal.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * §4.6 bear-snapshot/1 스키마의 `inputs` 서브 오브젝트 — [BearSnapshotEntity.inputsJson]에 그대로
 * 직렬화된다(별도 규약 금지). 필드명은 스키마 예시와 1:1(`s3_lossRatio`/`s4_marginCall` 등 표기
 * 그대로 — 대소문자 섞임도 SSOT).
 *
 * `axes`는 §4.6 "선택 필드"(프로토타입 jsx 미보유, v1 스코어링 미사용)이므로 v1.2 Phase 3.5-1은
 * 생략한다(§4.6 "v1은 생략 또는 정적 시드 직렬화만 허용" 중 생략을 채택).
 */
@Serializable
data class SnapshotInputsPayload(
    val markets: List<SnapshotMarketEntry>,
    @SerialName("s1_period") val s1Period: String,
    @SerialName("s2_up") val s2Up: Int,
    @SerialName("s2_down") val s2Down: Int,
    @SerialName("s2_deepening") val s2Deepening: Boolean,
    @SerialName("s3_lossRatio") val s3LossRatio: Double,
    @SerialName("s3_etf") val s3Etf: String,
    @SerialName("s3_bigDeal") val s3BigDeal: String,
    @SerialName("s4_rate") val s4Rate: Double,
    @SerialName("s4_dir") val s4Dir: String,
    @SerialName("s4_credit") val s4Credit: Double,
    @SerialName("s4_marginCall") val s4MarginCall: Boolean,
    @SerialName("amp_semiExport") val ampSemiExport: Double,
    @SerialName("amp_kospi2") val ampKospi2: Double,
    @SerialName("amp_buffer") val ampBuffer: Boolean
)

/** §4.6 `inputs.markets[]` 한 행 — [MarketReturns]와 동형(schema 예시 `{ "name": "코스피", "r": [...] }`). */
@Serializable
data class SnapshotMarketEntry(
    val name: String,
    val r: List<Double?>
)

/**
 * §4.6 `field_meta.<field>` 값 — [FieldSource]의 JSON 표현(날짜는 ISO-8601 문자열, 예: "2026-07-11").
 */
@Serializable
data class SnapshotFieldMetaEntry(
    val source: String,
    @SerialName("as_of") val asOf: String?,
    val origin: String?
)
