package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 도표48 국가별 지수 수익률 자동 수집 캐시 (TASK.md §2 `CountryReturnEntity`, §4 "해외 19개 지수").
 *
 * [BearSignalAutoCacheEntity](범용 스칼라 key-value)와 달리 국가별 수익률은 지수당 4기간 값을
 * 함께 가지는 구조라 전용 테이블로 분리했다(Room v34→v35, `MIGRATION_34_35`).
 *
 * @property countryName 지수명 (예: "코스피", "닛케이") — [BearSignalReportBaseline.MARKETS]와 동일 표기
 * @property r12m/[r6m]/[r3m]/[r1m] 누적수익률(%) — 미수집(커버리지 없음/데이터 부족)은 null
 * @property lead 주도 지수 여부 (코스피 = true)
 * @property coverage [com.tinyoscillator.feature.bearsignal.domain.model.MarketCoverage.name]
 * @property updatedAt 최신 갱신시각 (epoch millis)
 */
@Entity(tableName = "bear_signal_country_return")
data class BearSignalCountryReturnEntity(
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
    val lead: Boolean,
    val coverage: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
