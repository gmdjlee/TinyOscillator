package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "market_per",
    primaryKeys = ["market", "date"],
    indices = [
        Index(value = ["date"]),
        Index(value = ["market", "date"])
    ]
)
data class MarketPerEntity(
    @ColumnInfo(name = "market")
    val market: String,             // "KOSPI" or "KOSDAQ"

    @ColumnInfo(name = "date")
    val date: String,               // "yyyyMMdd"

    @ColumnInfo(name = "close_index")
    val closeIndex: Double,

    @ColumnInfo(name = "per")
    val per: Double,

    @ColumnInfo(name = "pbr")
    val pbr: Double,

    @ColumnInfo(name = "dividend_yield")
    val dividendYield: Double
)
