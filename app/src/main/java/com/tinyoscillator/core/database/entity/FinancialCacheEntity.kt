package com.tinyoscillator.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "financial_cache")
data class FinancialCacheEntity(
    @PrimaryKey val ticker: String,
    val name: String,
    val data: String,      // JSON (FinancialDataCache)
    val cachedAt: Long = System.currentTimeMillis()
)
