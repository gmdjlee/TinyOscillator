package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_themes")
data class UserThemeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tickers: String,          // JSON 배열: ["005930","000660"]
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
