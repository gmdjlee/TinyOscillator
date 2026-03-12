package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_master",
    indices = [Index(value = ["name"])]
)
data class StockMasterEntity(
    @PrimaryKey
    @ColumnInfo(name = "ticker")
    val ticker: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "market")
    val market: String,

    @ColumnInfo(name = "sector", defaultValue = "")
    val sector: String = "",

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long
)
