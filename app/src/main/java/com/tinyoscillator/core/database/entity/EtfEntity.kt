package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "etfs")
data class EtfEntity(
    @PrimaryKey
    @ColumnInfo(name = "ticker")
    val ticker: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "isin_code")
    val isinCode: String,

    @ColumnInfo(name = "index_name")
    val indexName: String? = null,

    @ColumnInfo(name = "total_fee")
    val totalFee: Double? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
