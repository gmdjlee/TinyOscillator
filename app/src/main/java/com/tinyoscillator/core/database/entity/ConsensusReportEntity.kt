package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "consensus_reports",
    primaryKeys = ["stock_ticker", "write_date", "author", "institution"],
    indices = [
        Index(value = ["write_date"]),
        Index(value = ["stock_ticker", "write_date"]),
        Index(value = ["institution"]),
        Index(value = ["category"])
    ]
)
data class ConsensusReportEntity(
    @ColumnInfo(name = "write_date")
    val writeDate: String,           // "2026-03-23"

    @ColumnInfo(name = "category")
    val category: String,            // 분류

    @ColumnInfo(name = "prev_opinion")
    val prevOpinion: String,         // 이전의견

    @ColumnInfo(name = "opinion")
    val opinion: String,             // 투자의견

    @ColumnInfo(name = "title")
    val title: String,               // 제목

    @ColumnInfo(name = "stock_ticker")
    val stockTicker: String,         // 종목코드

    @ColumnInfo(name = "stock_name", defaultValue = "")
    val stockName: String,           // 종목명

    @ColumnInfo(name = "author")
    val author: String,              // 작성자

    @ColumnInfo(name = "institution")
    val institution: String,         // 작성기관

    @ColumnInfo(name = "target_price")
    val targetPrice: Long,           // 목표가 (원)

    @ColumnInfo(name = "current_price")
    val currentPrice: Long,          // 현재가 (원)

    @ColumnInfo(name = "divergence_rate")
    val divergenceRate: Double       // 괴리율 (%)
)
