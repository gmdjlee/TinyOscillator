package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * BearSignal 자동/수동 입력 캐시 — 지표키·값·출처·갱신시각 (TASK.md §1.2 하이브리드 데이터 아키텍처).
 *
 * 범용 key-value 구조 — Phase 2+에서 [B] 등급 자동 지표를 추가할 때도 새 마이그레이션 없이
 * [com.tinyoscillator.feature.bearsignal.domain.model.BearIndicatorKey]에 키만 추가해 재사용한다.
 * Phase 1은 `source`가 항상 `AUTO`(자동 수집)인 행만 기록한다.
 *
 * @property indicatorKey [com.tinyoscillator.feature.bearsignal.domain.model.BearIndicatorKey.key] 값
 * @property value 지표 값 (Int 지표는 소수부 없이 저장)
 * @property source [com.tinyoscillator.feature.bearsignal.domain.model.InputSource.name]
 * @property updatedAt 최신 갱신시각 (epoch millis)
 */
@Entity(tableName = "bear_signal_auto_cache")
data class BearSignalAutoCacheEntity(
    @PrimaryKey
    @ColumnInfo(name = "indicator_key")
    val indicatorKey: String,
    val value: Double,
    val source: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
