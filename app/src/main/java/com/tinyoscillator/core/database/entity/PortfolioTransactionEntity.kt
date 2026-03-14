package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "portfolio_transactions",
    indices = [
        Index(value = ["holding_id"])
    ]
)
data class PortfolioTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "holding_id")
    val holdingId: Long,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "shares")
    val shares: Int,

    @ColumnInfo(name = "price_per_share")
    val pricePerShare: Int,

    @ColumnInfo(name = "memo", defaultValue = "")
    val memo: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
