package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 점진적 학습 모델 상태 저장 엔티티.
 *
 * IncrementalNaiveBayes, IncrementalLogisticRegression 등의 학습 상태를 JSON으로 직렬화하여 저장.
 */
@Entity(tableName = "incremental_model_state")
data class IncrementalModelStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_name")
    val modelName: String,

    @ColumnInfo(name = "state_json")
    val stateJson: String,

    @ColumnInfo(name = "samples_seen")
    val samplesSeen: Int = 0,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
