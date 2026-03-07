package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "etf_holdings",
    primaryKeys = ["etf_ticker", "stock_ticker", "date"],
    indices = [
        Index(value = ["date"]),
        Index(value = ["etf_ticker", "date"]),
        Index(value = ["stock_ticker"])
    ]
)
data class EtfHoldingEntity(
    @ColumnInfo(name = "etf_ticker")
    val etfTicker: String,

    @ColumnInfo(name = "stock_ticker")
    val stockTicker: String,

    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "stock_name")
    val stockName: String,

    @ColumnInfo(name = "weight")
    val weight: Double? = null,

    @ColumnInfo(name = "shares")
    val shares: Long = 0,

    @ColumnInfo(name = "amount")
    val amount: Long = 0
)
