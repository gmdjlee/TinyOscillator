package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.BearSnapshot
import com.tinyoscillator.feature.bearsignal.domain.model.FieldSource
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.SnapshotFieldMetaEntry
import com.tinyoscillator.feature.bearsignal.domain.model.SnapshotInputsPayload
import com.tinyoscillator.feature.bearsignal.domain.model.SnapshotMarketEntry
import com.tinyoscillator.feature.bearsignal.domain.model.ValueSource
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * §4.6 bear-snapshot/1 스키마 직렬화 — [ObserveBearSignalStateUseCase.State]를 Room 저장용
 * [BearSnapshot](`inputsJson`/`fieldMetaJson`)으로 변환한다 (TASK_bear_signal_console.md §6.1 Phase 3.5-1).
 *
 * 스키마 필드명·구조는 §4.6 예시와 1:1(SSOT) — 임의 변경 금지. `field_meta`는
 * [MergeBearSignalInputsUseCase]의 병합 우선순위(MANUAL 〉 AUTO 〉 리포트 기준값)를 그대로
 * 재현한다 — 즉 이 UseCase는 병합 로직을 다시 구현하지 않고 [ObserveBearSignalStateUseCase.State]가
 * 이미 들고 있는 `auto`/`manual` 원본으로부터 "그 필드가 어느 경로에서 채택됐는지"만 역산한다.
 *
 * 순수 함수(kotlinx.serialization만 사용), 안드로이드 의존성 0(JVM 단위테스트 대상).
 */
class BuildBearSnapshotUseCase {

    private val json = Json { encodeDefaults = true }

    /**
     * @param state Room 4-Flow 합성 상태([ObserveBearSignalStateUseCase.State])
     * @param day 스냅샷 날짜(as_of) — 기본키
     * @param configBasis §4.6 `config_basis`(예: [BearSignalReportBaseline.CONFIG_BASIS])
     * @param createdAt 생성 시각(epoch millis)
     */
    operator fun invoke(
        state: ObserveBearSignalStateUseCase.State,
        day: LocalDate,
        configBasis: String,
        createdAt: Long
    ): BearSnapshot {
        val inputsPayload = toInputsPayload(state.inputs)
        val fieldMetaPayload = buildFieldMeta(state.auto, state.manual).mapValues { (_, v) -> v.toPayload() }

        return BearSnapshot(
            day = day.toString(),
            phase = state.result.phase,
            lead = state.result.lead,
            gate = state.result.gate,
            s1 = state.result.s1,
            s2 = state.result.s2,
            s3 = state.result.s3,
            amp = state.result.amp,
            configBasis = configBasis,
            inputsJson = json.encodeToString(SnapshotInputsPayload.serializer(), inputsPayload),
            fieldMetaJson = json.encodeToString(
                MapSerializer(String.serializer(), SnapshotFieldMetaEntry.serializer()),
                fieldMetaPayload
            ),
            createdAt = createdAt
        )
    }

    private fun toInputsPayload(inputs: BearSignalInputs): SnapshotInputsPayload = SnapshotInputsPayload(
        markets = inputs.markets.map { SnapshotMarketEntry(it.name, it.r) },
        s1Period = PERIOD_LABELS.getOrElse(inputs.periodIdx) { PERIOD_LABELS.last() },
        s2Up = inputs.up,
        s2Down = inputs.down,
        s2Deepening = inputs.deepening,
        s3LossRatio = inputs.loss,
        s3Etf = inputs.etf,
        s3BigDeal = inputs.big,
        s4Rate = inputs.rate,
        s4Dir = inputs.dir,
        s4Credit = inputs.credit,
        s4MarginCall = inputs.margin,
        ampSemiExport = inputs.semi,
        ampKospi2 = inputs.kospi2,
        ampBuffer = inputs.buffer
    )

    /**
     * [MergeBearSignalInputsUseCase.invoke]의 필드별 우선순위를 그대로 역산한다(스칼라 필드만 —
     * `markets`/`s1_period`는 §4.6 예시상 field_meta 대상이 아니다. `s2_deepening`은
     * [MergeBearSignalInputsUseCase]에 auto/manual 경로 자체가 없어 항상 [ValueSource.BASELINE]).
     */
    private fun buildFieldMeta(
        auto: AutoBearSignalInputs?,
        manual: ManualBearSignalInputs
    ): Map<String, FieldSource> = mapOf(
        "s2_up" to autoOrBaseline(auto?.up3, ORIGIN_KRX),
        "s2_down" to autoOrBaseline(auto?.down3, ORIGIN_KRX),
        "s2_deepening" to FieldSource(ValueSource.BASELINE, null, ORIGIN_BASELINE),
        "s3_lossRatio" to manualOrBaseline(manual.loss),
        "s3_etf" to autoOrBaseline(auto?.etf, ORIGIN_IPO),
        "s3_bigDeal" to manualOrBaseline(manual.big),
        "s4_rate" to autoOrBaseline(auto?.rate, ORIGIN_FRED),
        "s4_dir" to manualAutoOrBaseline(manual.dir, auto?.dir, ORIGIN_ECOS),
        "s4_credit" to manualOrBaseline(manual.credit),
        "s4_marginCall" to manualOrBaseline(manual.margin),
        "amp_semiExport" to autoOrBaseline(auto?.semi, ORIGIN_CUSTOMS),
        "amp_kospi2" to autoOrBaseline(auto?.kospi2, ORIGIN_MARKETCAP),
        "amp_buffer" to autoOrBaseline(auto?.buffer, ORIGIN_CUSTOMS)
    )

    private fun manualOrBaseline(indicator: AutoIndicator<*>?): FieldSource =
        if (indicator != null) {
            FieldSource(ValueSource.MANUAL, toLocalDate(indicator.updatedAt), ORIGIN_USER)
        } else {
            FieldSource(ValueSource.BASELINE, null, ORIGIN_BASELINE)
        }

    private fun autoOrBaseline(indicator: AutoIndicator<*>?, origin: String): FieldSource =
        if (indicator != null) {
            FieldSource(ValueSource.AUTO, toLocalDate(indicator.updatedAt), origin)
        } else {
            FieldSource(ValueSource.BASELINE, null, ORIGIN_BASELINE)
        }

    /** `dir`처럼 MANUAL(Phase 3)·AUTO(Phase 2) 양쪽 경로가 모두 있는 필드 — MANUAL 우선(§4.6 우선순위). */
    private fun manualAutoOrBaseline(
        manual: AutoIndicator<*>?,
        auto: AutoIndicator<*>?,
        autoOrigin: String
    ): FieldSource = when {
        manual != null -> FieldSource(ValueSource.MANUAL, toLocalDate(manual.updatedAt), ORIGIN_USER)
        auto != null -> FieldSource(ValueSource.AUTO, toLocalDate(auto.updatedAt), autoOrigin)
        else -> FieldSource(ValueSource.BASELINE, null, ORIGIN_BASELINE)
    }

    private fun toLocalDate(epochMilli: Long): LocalDate =
        Instant.ofEpochMilli(epochMilli).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun FieldSource.toPayload(): SnapshotFieldMetaEntry = SnapshotFieldMetaEntry(
        source = source.name,
        asOf = asOf?.toString(),
        origin = origin
    )

    companion object {
        /** periodIdx 0=12M..3=1M → §4.6 `s1_period` 라벨("12m".."1m") */
        private val PERIOD_LABELS = listOf("12m", "6m", "3m", "1m")

        private const val ORIGIN_KRX = "kotlin_krx:KS11"
        private const val ORIGIN_IPO = "yahoo|stooq:IPO"
        private const val ORIGIN_FRED = "FRED:DFEDTARU"
        private const val ORIGIN_ECOS = "ECOS:base_rate"
        private const val ORIGIN_CUSTOMS = "관세청:nitemtrade"
        private const val ORIGIN_MARKETCAP = "kotlin_krx:market_cap"
        private const val ORIGIN_USER = "user"
        private const val ORIGIN_BASELINE = "report_baseline"
    }
}
