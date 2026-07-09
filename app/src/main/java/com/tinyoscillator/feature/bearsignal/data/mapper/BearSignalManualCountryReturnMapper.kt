package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalManualCountryReturnEntity
import com.tinyoscillator.feature.bearsignal.domain.model.ManualMarketReturn

/** [ManualMarketReturn] ↔ [BearSignalManualCountryReturnEntity] 변환 (data 계층, Phase 3). */
object BearSignalManualCountryReturnMapper {

    fun toEntity(manual: ManualMarketReturn): BearSignalManualCountryReturnEntity = BearSignalManualCountryReturnEntity(
        countryName = manual.name,
        r12m = manual.r.getOrNull(0),
        r6m = manual.r.getOrNull(1),
        r3m = manual.r.getOrNull(2),
        r1m = manual.r.getOrNull(3),
        updatedAt = manual.updatedAt
    )

    fun toDomain(entities: List<BearSignalManualCountryReturnEntity>): List<ManualMarketReturn> =
        entities.map { toDomain(it) }

    fun toDomain(entity: BearSignalManualCountryReturnEntity): ManualMarketReturn = ManualMarketReturn(
        name = entity.countryName,
        r = listOf(entity.r12m, entity.r6m, entity.r3m, entity.r1m),
        updatedAt = entity.updatedAt
    )
}
