package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * KIS 종목정보(search-stock-info, TR_ID=CTPF1002R) 응답에서 추출한 업종 마스터.
 *
 * KIS는 업종 목록 전용 엔드포인트를 제공하지 않으므로,
 * 스톡마스터의 각 섹터별 대표 종목 1건씩을 호출해 응답의
 * `idx_bztp_{lcls|mcls|scls}_cd/_name` 필드를 dedupe 저장한다.
 *
 * [code]는 KIS 업종지수 코드이며, 지수 차트 조회 시 `FID_INPUT_ISCD`로 사용된다.
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
