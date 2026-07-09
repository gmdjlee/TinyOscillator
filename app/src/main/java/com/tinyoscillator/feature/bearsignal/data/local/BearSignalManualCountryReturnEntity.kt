package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 도표48 국가별 지수 수익률 수동 오버라이드 캐시 (TASK.md §4 "해외 19개 지수" 폴백, §5.3 인라인 편집,
 * Phase 3). [BearSignalCountryReturnEntity](자동 수집 전용)와 분리된 전용 테이블 — 이유는
 * [BearSignalManualInputEntity]와 동일(자동 갱신 덮어쓰기 회피).
 *
 * @property countryName 지수명 — [com.tinyoscillator.feature.bearsignal.domain.model.BearSignalReportBaseline.MARKETS]와 동일 표기
 * @property r12m/[r6m]/[r3m]/[r1m] 수동 입력 누적수익률(%) — 기간별로 미입력이면 null(그 기간만 AUTO/기준값 폴백)
 * @property updatedAt 최신 수동 입력시각 (epoch millis)
 */
@Entity(tableName = "bear_signal_manual_country_return")
data class BearSignalManualCountryReturnEntity(
    @PrimaryKey
    @ColumnInfo(name = "country_name")
    val countryName: String,
    @ColumnInfo(name = "r_12m")
    val r12m: Double?,
    @ColumnInfo(name = "r_6m")
    val r6m: Double?,
    @ColumnInfo(name = "r_3m")
    val r3m: Double?,
    @ColumnInfo(name = "r_1m")
    val r1m: Double?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
