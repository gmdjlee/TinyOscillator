package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * HMM 레짐 분류기 학습 상태 영속화
 *
 * 모델 파라미터를 JSON으로 직렬화하여 저장.
 * 주 1회 WorkManager 작업으로 재학습 후 갱신.
 */
@Entity(tableName = "regime_state")
data class RegimeStateEntity(
    @PrimaryKey
    val id: String = "market_regime",  // single-row table
    @ColumnInfo(name = "state_json")
    val stateJson: String,             // JSON-serialized HMM model
    @ColumnInfo(name = "regime_name")
    val regimeName: String,            // current regime name
    @ColumnInfo(name = "confidence")
    val confidence: Double,
    @ColumnInfo(name = "trained_at")
    val trainedAt: Long = System.currentTimeMillis()
)
