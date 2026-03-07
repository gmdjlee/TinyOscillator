package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "analysis_cache",
    primaryKeys = ["ticker", "date"]
)
data class AnalysisCacheEntity(
    @ColumnInfo(name = "ticker")
    val ticker: String,

    @ColumnInfo(name = "date")
    val date: String,           // "yyyyMMdd"

    @ColumnInfo(name = "market_cap")
    val marketCap: Long,        // 시가총액 (원)

    @ColumnInfo(name = "foreign_net")
    val foreignNet: Long,       // 외국인 순매수 (원)

    @ColumnInfo(name = "inst_net")
    val instNet: Long,          // 기관 순매수 (원)

    @ColumnInfo(name = "close_price", defaultValue = "0")
    val closePrice: Int = 0     // 종가 (원)
)
