package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watchlist_items",
    indices = [
        Index("ticker", unique = true),
        Index("group_id"),
    ]
)
data class WatchlistItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "ticker")
    val ticker: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "group_id")
    val groupId: Long = 0,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "cached_price")
    val cachedPrice: Long? = null,

    @ColumnInfo(name = "cached_change")
    val cachedChange: Float? = null,

    @ColumnInfo(name = "cached_signal")
    val cachedSignal: Float? = null,

    @ColumnInfo(name = "price_updated_at")
    val priceUpdatedAt: Long = 0L,
)
