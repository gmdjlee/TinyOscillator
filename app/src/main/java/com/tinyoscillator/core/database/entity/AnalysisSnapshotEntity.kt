package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 분석 결과 스냅샷 Entity.
 * 확률 분석 실행 시 각 알고리즘의 점수와 앙상블 결과를 저장하여
 * 동일 종목의 과거 분석과 비교할 수 있게 한다.
 */
@Entity(
    tableName = "analysis_snapshots",
    indices = [
        Index(value = ["ticker", "analyzed_at"]),
        Index(value = ["ticker"])
    ]
)
data class AnalysisSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val ticker: String,
    val name: String,

    @ColumnInfo(name = "analyzed_at")
    val analyzedAt: Long,

    @ColumnInfo(name = "ensemble_score")
    val ensembleScore: Double,

    /** JSON: {"naiveBayes": 0.78, "logistic": 0.87, ...} */
    @ColumnInfo(name = "algo_scores")
    val algoScores: String,

    /** JSON: {"naiveBayes": "상승78% marketCap기여", ...} */
    @ColumnInfo(name = "algo_rationales")
    val algoRationales: String,

    /** AI 구조화 해석 JSON (StockAnalysis 스키마) — 동일 스냅샷 재조회 시 API 호출 없이 재사용 */
    @ColumnInfo(name = "ai_interpretation")
    val aiInterpretation: String? = null
)
