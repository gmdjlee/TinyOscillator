package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * KOSPI 종합지수 일별 종가 캐시
 *
 * 레짐 분류기 학습 및 예측에 사용.
 * 최근 504일(2년) 보관, 1일 TTL.
 */
@Entity(
    tableName = "kospi_index",
    indices = [Index(value = ["date"], unique = true)]
)
data class KospiIndexEntity(
    @PrimaryKey
    val date: String,           // yyyyMMdd
    @ColumnInfo(name = "close_value")
    val closeValue: Double,     // KOSPI 종가
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
