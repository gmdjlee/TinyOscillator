package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * KIS 업종분류코드 마스터 (정적 시드).
 *
 * [com.tinyoscillator.data.seed.KisSectorCodeSeed]의 상수 테이블에서 씨드되며, 네트워크 호출
 * 없이 즉시 반영된다. [code]는 4자리 KIS 업종분류코드 (예: 0001 코스피, 1001 코스닥,
 * 0013 코스피 전기전자)이며 KIS 업종지수 차트(TR_ID=FHKUP03500100) 조회 시 `FID_INPUT_ISCD`로
 * 그대로 전달된다. [level]은 [com.tinyoscillator.domain.model.SectorLevel] 코드
 * (1=대표지수, 2=코스피 업종, 3=코스닥 업종).
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

    /** 1=대표지수, 2=코스피 업종, 3=코스닥 업종 */
    @ColumnInfo(name = "level")
    val level: Int,

    @ColumnInfo(name = "parent_code")
    val parentCode: String?,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long,
)
