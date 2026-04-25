package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Kiwoom ka90002(테마구성종목요청) 응답 캐시.
 *
 * 복합 PK `(theme_code, stock_code)` — 동일 테마·동일 종목은 1건만 유지.
 * `stock_code` 인덱스를 별도로 두어 "어떤 테마들이 이 종목을 포함하는가" 역인덱스 조회를 지원한다.
 */
@Entity(
    tableName = "theme_stock",
    primaryKeys = ["theme_code", "stock_code"],
    indices = [
        Index(value = ["stock_code"]),
        Index(value = ["theme_code"]),
    ],
)
data class ThemeStockEntity(
    @ColumnInfo(name = "theme_code")
    val themeCode: String,

    @ColumnInfo(name = "stock_code")
    val stockCode: String,

    @ColumnInfo(name = "stock_name")
    val stockName: String,

    @ColumnInfo(name = "current_price", defaultValue = "0")
    val currentPrice: Double,

    /** 전일 대비 (원). `pred_pre`. */
    @ColumnInfo(name = "prior_diff", defaultValue = "0")
    val priorDiff: Double,

    @ColumnInfo(name = "flu_rate", defaultValue = "0")
    val fluRate: Double,

    @ColumnInfo(name = "volume", defaultValue = "0")
    val volume: Long,

    /** 기간수익률 (%). ka90002의 `dt_prft_rt`. */
    @ColumnInfo(name = "period_return_rate", defaultValue = "0")
    val periodReturnRate: Double,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long,
)
