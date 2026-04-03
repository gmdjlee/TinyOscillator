package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * 앙상블 학습 이력 — 스태킹 메타 학습기 OOF 학습 데이터.
 *
 * 각 분석 시점에 9개 알고리즘의 보정된 확률을 저장하고,
 * T+1 거래일에 실제 결과(actualOutcome)를 채운다.
 * actualOutcome이 채워진 레코드만 메타 학습기 학습에 사용.
 */
@Entity(
    tableName = "ensemble_history",
    primaryKeys = ["ticker", "date"],
    indices = [
        Index(value = ["date"]),
        Index(value = ["ticker"]),
        Index(value = ["actual_outcome"])
    ]
)
data class EnsembleHistoryEntity(
    val ticker: String,
    val date: String,
    /** JSON-serialized Map<String, Double> — {algo_name: calibrated_prob} */
    @ColumnInfo(name = "signals_json") val signalsJson: String,
    /** 1=상승, 0=하락, null=아직 미확인 */
    @ColumnInfo(name = "actual_outcome") val actualOutcome: Int? = null,
    /** 다음날 종가 기준 수익률 (소수점) */
    @ColumnInfo(name = "next_day_return") val nextDayReturn: Double? = null,
    /** 분석 시 시장 레짐 (nullable) */
    @ColumnInfo(name = "regime_id") val regimeId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
