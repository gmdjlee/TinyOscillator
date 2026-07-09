package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalCountryReturnEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AutoMarketReturn
import com.tinyoscillator.feature.bearsignal.domain.model.MarketCoverage
import com.tinyoscillator.feature.bearsignal.domain.model.MarketReturnsSnapshot

/** [MarketReturnsSnapshot] ↔ [BearSignalCountryReturnEntity] 변환 (data 계층). */
object BearSignalCountryReturnMapper {

    fun toEntities(snapshot: MarketReturnsSnapshot): List<BearSignalCountryReturnEntity> =
        snapshot.markets.map { toEntity(it) }

    fun toEntity(market: AutoMarketReturn): BearSignalCountryReturnEntity = BearSignalCountryReturnEntity(
        countryName = market.name,
        r12m = market.r.getOrNull(0),
        r6m = market.r.getOrNull(1),
        r3m = market.r.getOrNull(2),
        r1m = market.r.getOrNull(3),
        lead = market.lead,
        coverage = market.coverage.name,
        updatedAt = market.updatedAt
    )

    /** 빈 리스트는 null(미수집)로 취급 — [com.tinyoscillator.feature.bearsignal.data.local.BearSignalDao] 관례와 동일 */
    fun toDomain(entities: List<BearSignalCountryReturnEntity>): MarketReturnsSnapshot? {
        if (entities.isEmpty()) return null
        return MarketReturnsSnapshot(markets = entities.map { toAutoMarketReturn(it) })
    }

    private fun toAutoMarketReturn(entity: BearSignalCountryReturnEntity): AutoMarketReturn = AutoMarketReturn(
        name = entity.countryName,
        r = listOf(entity.r12m, entity.r6m, entity.r3m, entity.r1m),
        lead = entity.lead,
        coverage = runCatching { MarketCoverage.valueOf(entity.coverage) }.getOrDefault(MarketCoverage.MANUAL_REQUIRED),
        updatedAt = entity.updatedAt
    )
}
