package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "worker_logs",
    indices = [
        Index(value = ["worker_name"]),
        Index(value = ["executed_at"])
    ]
)
data class WorkerLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "worker_name")
    val workerName: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "error_detail")
    val errorDetail: String? = null,

    @ColumnInfo(name = "executed_at")
    val executedAt: Long = System.currentTimeMillis()
)
