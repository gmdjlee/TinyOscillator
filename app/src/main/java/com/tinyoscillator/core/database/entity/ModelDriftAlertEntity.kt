package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 모델 드리프트 알림 엔티티.
 *
 * 점진적 학습 모델의 Brier 점수가 베이스라인 대비 5% 이상 열화 시 기록.
 */
@Entity(
    tableName = "model_drift_alert",
    indices = [
        Index("model_name"),
        Index("detected_at")
    ]
)
data class ModelDriftAlertEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "brier_score")
    val brierScore: Double,

    @ColumnInfo(name = "baseline_brier")
    val baselineBrier: Double,

    @ColumnInfo(name = "degradation")
    val degradation: Double,

    @ColumnInfo(name = "detected_at")
    val detectedAt: Long = System.currentTimeMillis()
)
