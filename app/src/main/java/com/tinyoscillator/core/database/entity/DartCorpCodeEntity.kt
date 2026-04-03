package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * DART corp_code ↔ 종목코드 매핑 캐시
 *
 * DART API는 8자리 corp_code를 사용하며, 주식 종목코드(6자리)와 별개.
 * corpCode.xml 마스터 파일에서 파싱하여 캐싱 (30일 TTL).
 */
@Entity(
    tableName = "dart_corp_code",
    indices = [
        Index(value = ["corp_code"], unique = true)
    ]
)
data class DartCorpCodeEntity(
    /** 종목코드 (6자리, PK) */
    @PrimaryKey
    @ColumnInfo(name = "ticker")
    val ticker: String,

    /** DART 고유번호 (8자리) */
    @ColumnInfo(name = "corp_code")
    val corpCode: String,

    /** 회사명 */
    @ColumnInfo(name = "corp_name")
    val corpName: String,

    /** 캐시 시점 (epoch millis) */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
