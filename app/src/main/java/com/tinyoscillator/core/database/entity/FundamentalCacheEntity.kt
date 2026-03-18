package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "fundamental_cache",
    primaryKeys = ["ticker", "date"]
)
data class FundamentalCacheEntity(
    @ColumnInfo(name = "ticker")
    val ticker: String,

    @ColumnInfo(name = "date")
    val date: String,           // "yyyyMMdd"

    @ColumnInfo(name = "close")
    val close: Long,

    @ColumnInfo(name = "eps")
    val eps: Long,

    @ColumnInfo(name = "per")
    val per: Double,

    @ColumnInfo(name = "bps")
    val bps: Long,

    @ColumnInfo(name = "pbr")
    val pbr: Double,

    @ColumnInfo(name = "dps")
    val dps: Long,

    @ColumnInfo(name = "dividend_yield")
    val dividendYield: Double
)
