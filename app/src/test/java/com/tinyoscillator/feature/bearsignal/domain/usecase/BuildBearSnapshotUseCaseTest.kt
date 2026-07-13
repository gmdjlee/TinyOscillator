package com.tinyoscillator.feature.bearsignal.domain.usecase

import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearPhase
import com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline
import com.tinyoscillator.feature.bearsignal.domain.model.BearThresholdsFixture
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.SnapshotFieldMetaEntry
import com.tinyoscillator.feature.bearsignal.domain.model.SnapshotInputsPayload
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [BuildBearSnapshotUseCase] 테스트 — §4.6 bear-snapshot/1 스키마 직렬화(SSOT) + field_meta
 * 병합 우선순위(MANUAL 〉 AUTO 〉 BASELINE) 재현을 검증한다.
 */
class BuildBearSnapshotUseCaseTest {

    private val useCase = BuildBearSnapshotUseCase()
    private val merge = MergeBearSignalInputsUseCase()
    private val compute = ComputeBearSignalUseCase(BearThresholdsFixture.DEFAULT)
    private val json = Json { encodeDefaults = true }

    private fun decodeInputs(inputsJson: String): SnapshotInputsPayload =
        json.decodeFromString(SnapshotInputsPayload.serializer(), inputsJson)

    private fun decodeFieldMeta(fieldMetaJson: String): Map<String, SnapshotFieldMetaEntry> =
        json.decodeFromString(MapSerializer(String.serializer(), SnapshotFieldMetaEntry.serializer()), fieldMetaJson)

    @Test
    fun `골든 케이스 - auto·manual 전무면 전 필드 BASELINE이고 phase는 AMBER를 재현한다`() {
        val inputs = merge(auto = null, manual = ManualBearSignalInputs(), marketsSnapshot = null)
        val result = compute(inputs)
        val state = ObserveBearSignalStateUseCase.State(
            inputs = inputs,
            result = result,
            auto = null,
            manual = ManualBearSignalInputs(),
            marketsSnapshot = null,
            manualMarkets = emptyList()
        )

        val snapshot = useCase(state, LocalDate.parse("2026-07-11"), BearSignalReportBaseline.CONFIG_BASIS, 1_000L)

        assertEquals("2026-07-11", snapshot.day)
        assertEquals(BearPhase.AMBER, snapshot.phase)
        assertEquals(1, snapshot.s1)
        assertEquals(1, snapshot.s2)
        assertEquals(1, snapshot.s3)
        assertEquals(1, snapshot.gate)
        assertEquals(1.30, snapshot.amp, 1e-9)
        assertEquals(BearSignalReportBaseline.CONFIG_BASIS, snapshot.configBasis)
        assertEquals(1_000L, snapshot.createdAt)

        val payload = decodeInputs(snapshot.inputsJson)
        assertEquals("1m", payload.s1Period)
        assertEquals(14, payload.s2Up)
        assertEquals(12, payload.s2Down)
        assertEquals(true, payload.s2Deepening)
        assertEquals(45.0, payload.s3LossRatio, 1e-9)
        assertEquals("up", payload.s3Etf)
        assertEquals("pending", payload.s3BigDeal)
        assertEquals(3.75, payload.s4Rate, 1e-9)
        assertEquals("hike", payload.s4Dir)
        assertEquals(38.0, payload.s4Credit, 1e-9)
        assertEquals(false, payload.s4MarginCall)
        assertEquals(23.1, payload.ampSemiExport, 1e-9)
        assertEquals(56.0, payload.ampKospi2, 1e-9)
        assertEquals(true, payload.ampBuffer)
        assertEquals(20, payload.markets.size)
        assertEquals("코스피", payload.markets[0].name)
        assertEquals(listOf(173.1, 103.7, 54.0, 4.5), payload.markets[0].r)

        val fieldMeta = decodeFieldMeta(snapshot.fieldMetaJson)
        assertEquals(13, fieldMeta.size)
        fieldMeta.values.forEach { entry ->
            assertEquals("BASELINE", entry.source)
            assertNull(entry.asOf)
        }
    }

    @Test
    fun `AUTO 지표가 있으면 해당 필드의 field_meta가 AUTO로 채워진다`() {
        val autoUpdatedAtMillis = LocalDate.parse("2026-07-05")
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val auto = AutoBearSignalInputs(
            up3 = AutoIndicator(14, InputSource.AUTO, autoUpdatedAtMillis),
            down3 = AutoIndicator(12, InputSource.AUTO, autoUpdatedAtMillis),
            up4 = AutoIndicator(2, InputSource.AUTO, autoUpdatedAtMillis),
            down4 = AutoIndicator(1, InputSource.AUTO, autoUpdatedAtMillis),
            kospi2 = AutoIndicator(56.0, InputSource.AUTO, autoUpdatedAtMillis),
            semi = AutoIndicator(23.1, InputSource.AUTO, autoUpdatedAtMillis),
            buffer = AutoIndicator(true, InputSource.AUTO, autoUpdatedAtMillis),
            rate = AutoIndicator(3.75, InputSource.AUTO, autoUpdatedAtMillis),
            dir = AutoIndicator("hike", InputSource.AUTO, autoUpdatedAtMillis),
            etf = AutoIndicator("up", InputSource.AUTO, autoUpdatedAtMillis)
        )
        val inputs = merge(auto = auto, manual = ManualBearSignalInputs(), marketsSnapshot = null)
        val result = compute(inputs)
        val state = ObserveBearSignalStateUseCase.State(
            inputs = inputs,
            result = result,
            auto = auto,
            manual = ManualBearSignalInputs(),
            marketsSnapshot = null,
            manualMarkets = emptyList()
        )

        val snapshot = useCase(state, LocalDate.parse("2026-07-11"), BearSignalReportBaseline.CONFIG_BASIS, 1_000L)
        val fieldMeta = decodeFieldMeta(snapshot.fieldMetaJson)

        assertEquals("AUTO", fieldMeta.getValue("s2_up").source)
        assertEquals("2026-07-05", fieldMeta.getValue("s2_up").asOf)
        assertEquals("kotlin_krx:KS11", fieldMeta.getValue("s2_up").origin)
        assertEquals("AUTO", fieldMeta.getValue("s4_rate").source)
        assertEquals("FRED:DFEDTARU", fieldMeta.getValue("s4_rate").origin)
        assertEquals("AUTO", fieldMeta.getValue("s4_dir").source)
        assertEquals("AUTO", fieldMeta.getValue("amp_semiExport").source)
        assertEquals("AUTO", fieldMeta.getValue("amp_kospi2").source)
        assertEquals("AUTO", fieldMeta.getValue("amp_buffer").source)
        assertEquals("AUTO", fieldMeta.getValue("s3_etf").source)
        // manual 경로가 없는 필드는 AUTO가 있어도 BASELINE으로 남는다(§3 스코어링 미사용/merge 미대상)
        assertEquals("BASELINE", fieldMeta.getValue("s2_deepening").source)
    }

    @Test
    fun `MANUAL이 있으면 AUTO보다 우선한다 - dir 3단 우선순위(MANUAL 큰 AUTO 큰 BASELINE)`() {
        val autoUpdatedAtMillis = LocalDate.parse("2026-07-01")
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val manualUpdatedAtMillis = LocalDate.parse("2026-07-09")
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

        val auto = AutoBearSignalInputs(
            up3 = AutoIndicator(14, InputSource.AUTO, autoUpdatedAtMillis),
            down3 = AutoIndicator(12, InputSource.AUTO, autoUpdatedAtMillis),
            up4 = AutoIndicator(2, InputSource.AUTO, autoUpdatedAtMillis),
            down4 = AutoIndicator(1, InputSource.AUTO, autoUpdatedAtMillis),
            kospi2 = AutoIndicator(56.0, InputSource.AUTO, autoUpdatedAtMillis),
            dir = AutoIndicator("ease", InputSource.AUTO, autoUpdatedAtMillis)
        )
        val manual = ManualBearSignalInputs(
            dir = AutoIndicator("hike", InputSource.MANUAL, manualUpdatedAtMillis),
            loss = AutoIndicator(60.0, InputSource.MANUAL, manualUpdatedAtMillis),
            credit = AutoIndicator(50.0, InputSource.MANUAL, manualUpdatedAtMillis),
            margin = AutoIndicator(true, InputSource.MANUAL, manualUpdatedAtMillis),
            big = AutoIndicator("failed", InputSource.MANUAL, manualUpdatedAtMillis)
        )
        val inputs = merge(auto = auto, manual = manual, marketsSnapshot = null)
        val result = compute(inputs)
        val state = ObserveBearSignalStateUseCase.State(
            inputs = inputs,
            result = result,
            auto = auto,
            manual = manual,
            marketsSnapshot = null,
            manualMarkets = emptyList()
        )

        val snapshot = useCase(state, LocalDate.parse("2026-07-11"), BearSignalReportBaseline.CONFIG_BASIS, 1_000L)
        val fieldMeta = decodeFieldMeta(snapshot.fieldMetaJson)

        assertEquals("MANUAL", fieldMeta.getValue("s4_dir").source)
        assertEquals("2026-07-09", fieldMeta.getValue("s4_dir").asOf)
        assertEquals("user", fieldMeta.getValue("s4_dir").origin)
        assertEquals("MANUAL", fieldMeta.getValue("s3_lossRatio").source)
        assertEquals("MANUAL", fieldMeta.getValue("s4_credit").source)
        assertEquals("MANUAL", fieldMeta.getValue("s4_marginCall").source)
        assertEquals("MANUAL", fieldMeta.getValue("s3_bigDeal").source)

        val payload = decodeInputs(snapshot.inputsJson)
        assertEquals("hike", payload.s4Dir) // MergeBearSignalInputsUseCase도 MANUAL 우선
    }
}
