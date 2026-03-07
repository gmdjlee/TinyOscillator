package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "market_oscillator",
    indices = [
        Index(value = ["date"]),
        Index(value = ["market", "date"])
    ]
)
data class MarketOscillatorEntity(
    @PrimaryKey
    val id: String,                    // "KOSPI-2025-01-01"
    val market: String,                // KOSPI or KOSDAQ
    val date: String,                  // yyyy-MM-dd
    @ColumnInfo(name = "index_value")
    val indexValue: Double,            // 지수 종가
    val oscillator: Double,            // -100 ~ 100
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)
