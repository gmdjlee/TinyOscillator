package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * BearSignal 일자별 스코어링 스냅샷 캐시 (TASK_bear_signal_console.md §6.1 Phase 3.5-1, Room v36→v37).
 *
 * [day]("YYYY-MM-DD")가 기본키 — 일 단위 upsert(같은 날 재계산해도 최신 값으로 덮어쓴다).
 * [inputsJson]/[fieldMetaJson]은 §4.6 bear-snapshot/1 스키마의 `inputs`/`field_meta` 서브 오브젝트를
 * 그대로 직렬화한 문자열이다(별도 규약 금지 —
 * [com.tinyoscillator.feature.bearsignal.domain.usecase.BuildBearSnapshotUseCase] 참조).
 *
 * 컬럼명은 TASK.md §6.1 코드 블록의 카멜케이스 Kotlin 프로퍼티명을 그대로 사용한다(SSOT 코드 블록
 * 1:1 재현 — 프로젝트의 일반적인 snake_case `@ColumnInfo` 관례에서 의도적으로 벗어남).
 */
@Entity(tableName = "bear_snapshot")
data class BearSnapshotEntity(
    @PrimaryKey val day: String,
    val phase: String,
    val lead: Int,
    val gate: Int,
    val s1: Int,
    val s2: Int,
    val s3: Int,
    val amp: Double,
    val configBasis: String,
    val inputsJson: String,
    val fieldMetaJson: String,
    val createdAt: Long
)
