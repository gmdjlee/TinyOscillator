package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 알고리즘별 원시 점수 이력 — 보정 학습 + 적중률 추적 데이터.
 *
 * 분석 시점에 rawScore를 저장하고, T+1/T+5/T+20 거래일 후 실제 수익률을 채운다.
 * outcomeReturn(= T+20)이 채워진 레코드는 보정기 학습에 사용.
 * outcomeT1이 채워진 레코드는 적중률 집계에 사용.
 */
@Entity(
    tableName = "signal_history",
    indices = [
        Index(value = ["algo_name", "date"]),
        Index(value = ["ticker", "date"])
    ]
)
data class SignalHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticker: String,
    @ColumnInfo(name = "algo_name") val algoName: String,
    @ColumnInfo(name = "raw_score") val rawScore: Double,
    val date: String,
    @ColumnInfo(name = "outcome_return") val outcomeReturn: Double? = null,
    @ColumnInfo(name = "outcome_t1", defaultValue = "NULL") val outcomeT1: Float? = null,
    @ColumnInfo(name = "outcome_t5", defaultValue = "NULL") val outcomeT5: Float? = null,
    @ColumnInfo(name = "outcome_t20", defaultValue = "NULL") val outcomeT20: Float? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
