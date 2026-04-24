package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * KRX 통합 지수 마스터 (정적 시드).
 *
 * [KrxIntegratedIndexSeed]의 상수 테이블에서 씨드되며, 네트워크 호출 없이 즉시 반영된다.
 * [code]는 4자리 KRX 지수 코드 (예: 5042 KRX 100, 5043 KRX 자동차)이며
 * KIS 업종지수 차트(FHPUP02140000) 조회 시 `FID_INPUT_ISCD`로 그대로 전달된다.
 * [level]은 [com.tinyoscillator.domain.model.SectorLevel] 코드 (1=대표지수, 2=KRX 섹터, 3=KRX 300 업종).
 */
@Entity(
    tableName = "sector_master",
    indices = [Index(value = ["level"]), Index(value = ["parent_code"])]
)
data class SectorMasterEntity(
    @PrimaryKey
    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** 1=대분류, 2=중분류, 3=소분류 */
    @ColumnInfo(name = "level")
    val level: Int,

    @ColumnInfo(name = "parent_code")
    val parentCode: String?,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long,
)
