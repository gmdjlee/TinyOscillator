package com.tinyoscillator.feature.bearsignal.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * BearSignal 수동 오버라이드([C]/[D] 등급 스칼라) 캐시 (TASK.md §1.2, §2 `ManualInputEntity`, Phase 3).
 *
 * [BearSignalAutoCacheEntity](자동 수집 전용)와 분리된 전용 테이블 — 자동 수집이 매 갱신마다
 * 해당 지표 행을 덮어쓰므로(Room v34), 같은 테이블에 수동값을 두면 다음 자동 갱신 때 유실된다.
 * 이 테이블은 오직 사용자가 BottomSheet로 편집한 값만 담으며, `source`는 항상 MANUAL이므로
 * 별도 컬럼을 두지 않는다(행이 존재 = MANUAL 오버라이드가 있다는 뜻, 부재 = AUTO/기준값 폴백).
 *
 * @property indicatorKey [com.tinyoscillator.feature.bearsignal.domain.model.ManualIndicatorKey.key] 값
 * @property value 지표 값 (Boolean/String 지표는 [com.tinyoscillator.feature.bearsignal.data.mapper.BearSignalManualInputMapper]가 Double로 인코딩)
 * @property updatedAt 최신 수동 입력시각 (epoch millis)
 */
@Entity(tableName = "bear_signal_manual_input")
data class BearSignalManualInputEntity(
    @PrimaryKey
    @ColumnInfo(name = "indicator_key")
    val indicatorKey: String,
    val value: Double,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
