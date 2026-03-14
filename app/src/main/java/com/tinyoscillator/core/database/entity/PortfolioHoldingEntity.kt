package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "portfolio_holdings",
    indices = [
        Index(value = ["portfolio_id"]),
        Index(value = ["ticker"])
    ]
)
data class PortfolioHoldingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "portfolio_id")
    val portfolioId: Long,

    @ColumnInfo(name = "ticker")
    val ticker: String,

    @ColumnInfo(name = "stock_name")
    val stockName: String,

    @ColumnInfo(name = "market")
    val market: String,

    @ColumnInfo(name = "sector")
    val sector: String,

    @ColumnInfo(name = "last_price", defaultValue = "0")
    val lastPrice: Int = 0,

    @ColumnInfo(name = "price_updated_at", defaultValue = "0")
    val priceUpdatedAt: Long = 0
)
