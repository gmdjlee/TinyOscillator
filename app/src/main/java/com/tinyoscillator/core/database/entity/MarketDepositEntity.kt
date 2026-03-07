package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "market_deposits")
data class MarketDepositEntity(
    @PrimaryKey
    val date: String,                  // yyyy-MM-dd
    @ColumnInfo(name = "deposit_amount")
    val depositAmount: Double,         // 고객예탁금 (억원)
    @ColumnInfo(name = "deposit_change")
    val depositChange: Double,         // 전일대비 (억원)
    @ColumnInfo(name = "credit_amount")
    val creditAmount: Double,          // 신용잔고 (억원)
    @ColumnInfo(name = "credit_change")
    val creditChange: Double,          // 전일대비 (억원)
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)
