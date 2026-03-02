package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "analysis_history",
    indices = [Index(value = ["last_analyzed_at"])]
)
data class AnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "ticker")
    val ticker: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "last_analyzed_at")
    val lastAnalyzedAt: Long    // epoch millis
)
