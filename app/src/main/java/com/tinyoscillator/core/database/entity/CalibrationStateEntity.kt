package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 보정기 직렬화 상태 — JSON 문자열로 저장.
 *
 * 앱 재시작 시 보정기를 복원하기 위해 사용.
 */
@Entity(tableName = "calibration_state")
data class CalibrationStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "algo_name") val algoName: String,
    val method: String,
    @ColumnInfo(name = "state_json") val stateJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)
