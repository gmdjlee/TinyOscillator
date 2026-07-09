package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalAutoCacheEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AutoBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.BearIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource

/** [AutoBearSignalInputs] ↔ [BearSignalAutoCacheEntity] 변환 (data 계층). */
object BearSignalAutoCacheMapper {

    /** 자동 수집 결과 → Room upsert용 엔티티 5건 */
    fun toEntities(inputs: AutoBearSignalInputs): List<BearSignalAutoCacheEntity> = listOf(
        intEntity(BearIndicatorKey.S2_UP3, inputs.up3),
        intEntity(BearIndicatorKey.S2_DOWN3, inputs.down3),
        intEntity(BearIndicatorKey.S2_UP4, inputs.up4),
        intEntity(BearIndicatorKey.S2_DOWN4, inputs.down4),
        doubleEntity(BearIndicatorKey.AMP_KOSPI2, inputs.kospi2)
    )

    /**
     * Room 캐시 → 도메인 모델. 필수 5개 키 중 하나라도 없으면 미수집 상태로 간주해 null 반환.
     */
    fun toDomain(entities: List<BearSignalAutoCacheEntity>): AutoBearSignalInputs? {
        val byKey = entities.associateBy { it.indicatorKey }
        val up3 = byKey[BearIndicatorKey.S2_UP3.key] ?: return null
        val down3 = byKey[BearIndicatorKey.S2_DOWN3.key] ?: return null
        val up4 = byKey[BearIndicatorKey.S2_UP4.key] ?: return null
        val down4 = byKey[BearIndicatorKey.S2_DOWN4.key] ?: return null
        val kospi2 = byKey[BearIndicatorKey.AMP_KOSPI2.key] ?: return null
        return AutoBearSignalInputs(
            up3 = toIntIndicator(up3),
            down3 = toIntIndicator(down3),
            up4 = toIntIndicator(up4),
            down4 = toIntIndicator(down4),
            kospi2 = toDoubleIndicator(kospi2)
        )
    }

    private fun intEntity(key: BearIndicatorKey, indicator: AutoIndicator<Int>): BearSignalAutoCacheEntity =
        BearSignalAutoCacheEntity(
            indicatorKey = key.key,
            value = indicator.value.toDouble(),
            source = indicator.source.name,
            updatedAt = indicator.updatedAt
        )

    private fun doubleEntity(key: BearIndicatorKey, indicator: AutoIndicator<Double>): BearSignalAutoCacheEntity =
        BearSignalAutoCacheEntity(
            indicatorKey = key.key,
            value = indicator.value,
            source = indicator.source.name,
            updatedAt = indicator.updatedAt
        )

    private fun toIntIndicator(entity: BearSignalAutoCacheEntity): AutoIndicator<Int> =
        AutoIndicator(entity.value.toInt(), parseSource(entity.source), entity.updatedAt)

    private fun toDoubleIndicator(entity: BearSignalAutoCacheEntity): AutoIndicator<Double> =
        AutoIndicator(entity.value, parseSource(entity.source), entity.updatedAt)

    private fun parseSource(raw: String): InputSource =
        runCatching { InputSource.valueOf(raw) }.getOrDefault(InputSource.AUTO)
}
