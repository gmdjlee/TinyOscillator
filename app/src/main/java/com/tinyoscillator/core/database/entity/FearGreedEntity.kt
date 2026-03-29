package com.tinyoscillator.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Fear & Greed 지수 데이터 Entity.
 * KOSPI/KOSDAQ 시장별 일별 복합 심리 지수와 MACD 오실레이터를 저장한다.
 */
@Entity(
    tableName = "fear_greed_index",
    indices = [
        Index(value = ["date"]),
        Index(value = ["market", "date"])
    ]
)
data class FearGreedEntity(
    @PrimaryKey
    val id: String,                 // "KOSPI-2024-01-01"
    val market: String,             // "KOSPI" or "KOSDAQ"
    val date: String,               // "yyyy-MM-dd"
    val indexValue: Double,          // 시장 지수 종가
    val fearGreedValue: Double,     // 0.0~1.0 복합 지수
    val oscillator: Double,          // MACD 히스토그램
    val rsi: Double,
    val momentum: Double,
    val putCallRatio: Double,
    val volatility: Double,
    val spread: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)
