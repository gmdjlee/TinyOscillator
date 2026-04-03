package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * BOK ECOS 매크로 지표 캐시 엔티티
 *
 * 각 지표×월 조합에 대해 원시값과 YoY 변화율을 저장.
 * Weekly TTL 캐시로 사용 (FeatureStore와 별도 — 시계열 데이터).
 */
@Entity(
    tableName = "macro_indicator",
    indices = [
        Index("indicator_key"),
        Index("year_month")
    ]
)
data class MacroIndicatorEntity(
    @PrimaryKey
    val id: String,                     // "{indicator_key}_{yearMonth}" 예: "base_rate_202601"

    @ColumnInfo(name = "indicator_key")
    val indicatorKey: String,           // base_rate, m2, iip, usd_krw, cpi

    @ColumnInfo(name = "year_month")
    val yearMonth: String,              // yyyyMM

    @ColumnInfo(name = "raw_value")
    val rawValue: Double,               // 원시값

    @ColumnInfo(name = "yoy_change")
    val yoyChange: Double? = null,      // YoY 변화율 (계산 후 저장)

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
