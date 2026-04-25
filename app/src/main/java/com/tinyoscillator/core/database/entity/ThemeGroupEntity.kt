package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Kiwoom ka90001(테마그룹별요청) 응답 캐시.
 *
 * 일 1회 [com.tinyoscillator.core.worker.ThemeUpdateWorker]가 갱신하며,
 * `theme_code` PK로 동일 테마는 1건만 유지된다. `period_return_rate`에 인덱스를 두어
 * "기간수익률 정렬" 정렬 모드에서 LIMIT 쿼리가 인덱스 스캔으로 끝나도록 한다.
 */
@Entity(
    tableName = "theme_group",
    indices = [Index(value = ["period_return_rate"])]
)
data class ThemeGroupEntity(
    @PrimaryKey
    @ColumnInfo(name = "theme_code")
    val themeCode: String,

    @ColumnInfo(name = "theme_name")
    val themeName: String,

    @ColumnInfo(name = "stock_count", defaultValue = "0")
    val stockCount: Int,

    /** 등락률 (%). `+`/`-` prefix는 DTO → 도메인 매핑 단계에서 정규화 */
    @ColumnInfo(name = "flu_rate", defaultValue = "0")
    val fluRate: Double,

    /** 기간수익률 (%). ka90001의 `dt_prft_rt`. */
    @ColumnInfo(name = "period_return_rate", defaultValue = "0")
    val periodReturnRate: Double,

    @ColumnInfo(name = "rise_count", defaultValue = "0")
    val riseCount: Int,

    @ColumnInfo(name = "fall_count", defaultValue = "0")
    val fallCount: Int,

    /** 콤마 구분 주요 종목명. 응답 `main_stk` 그대로 보존. */
    @ColumnInfo(name = "main_stocks", defaultValue = "''")
    val mainStocks: String,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long,
)
