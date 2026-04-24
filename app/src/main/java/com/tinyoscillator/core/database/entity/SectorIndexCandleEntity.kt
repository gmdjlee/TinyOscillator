package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * KIS 업종지수 일별 캔들 캐시 (`inquire-daily-indexchartprice`, TR_ID=FHPUP02140000).
 *
 * 복합 PK (code, date) — 동일 업종·동일 날짜는 1건만 유지.
 */
@Entity(
    tableName = "sector_index_candle",
    primaryKeys = ["code", "date"],
)
data class SectorIndexCandleEntity(
    @ColumnInfo(name = "code")
    val code: String,

    /** YYYYMMDD */
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "open")
    val open: Double,

    @ColumnInfo(name = "high")
    val high: Double,

    @ColumnInfo(name = "low")
    val low: Double,

    @ColumnInfo(name = "close")
    val close: Double,

    @ColumnInfo(name = "volume")
    val volume: Long,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long,
)
