package com.tinyoscillator.feature.bearsignal.data.mapper

import com.tinyoscillator.feature.bearsignal.data.local.BearSignalManualInputEntity
import com.tinyoscillator.feature.bearsignal.domain.model.AutoIndicator
import com.tinyoscillator.feature.bearsignal.domain.model.InputSource
import com.tinyoscillator.feature.bearsignal.domain.model.IpoBigConsumption
import com.tinyoscillator.feature.bearsignal.domain.model.ManualBearSignalInputs
import com.tinyoscillator.feature.bearsignal.domain.model.ManualIndicatorKey
import com.tinyoscillator.feature.bearsignal.domain.usecase.RateGateInputCalculator

/**
 * [ManualBearSignalInputs] ↔ [BearSignalManualInputEntity] 변환 (data 계층, Phase 3).
 *
 * 캐시 컬럼이 `value: Double` 하나뿐이므로(마이그레이션 회피, [BearSignalAutoCacheMapper]와 동일
 * 설계 의도) Boolean/String 지표는 다음과 같이 인코딩한다:
 * - `margin`(Boolean): true=1.0, false=0.0
 * - `big`(String, "smooth"/"pending"/"failed"): smooth=0.0, pending=1.0, failed=2.0
 * - `dir`(String, "ease"/"hold"/"hike"): ease=-1.0, hold=0.0, hike=1.0 ([BearSignalAutoCacheMapper]와 동일 코드값)
 */
object BearSignalManualInputMapper {

    private const val BOOL_TRUE = 1.0
    private const val BOOL_FALSE = 0.0

    private const val DIR_EASE_CODE = -1.0
    private const val DIR_HOLD_CODE = 0.0
    private const val DIR_HIKE_CODE = 1.0

    private const val BIG_SMOOTH_CODE = 0.0
    private const val BIG_PENDING_CODE = 1.0
    private const val BIG_FAILED_CODE = 2.0

    fun encodeBoolean(value: Boolean): Double = if (value) BOOL_TRUE else BOOL_FALSE

    fun decodeBoolean(value: Double): Boolean = value >= 0.5

    fun encodeDir(value: String): Double = when (value) {
        RateGateInputCalculator.DIR_EASE -> DIR_EASE_CODE
        RateGateInputCalculator.DIR_HIKE -> DIR_HIKE_CODE
        else -> DIR_HOLD_CODE
    }

    fun decodeDir(value: Double): String = when {
        value <= DIR_EASE_CODE + 0.5 -> RateGateInputCalculator.DIR_EASE
        value >= DIR_HIKE_CODE - 0.5 -> RateGateInputCalculator.DIR_HIKE
        else -> RateGateInputCalculator.DIR_HOLD
    }

    fun encodeBig(value: String): Double = when (value) {
        IpoBigConsumption.SMOOTH -> BIG_SMOOTH_CODE
        IpoBigConsumption.FAILED -> BIG_FAILED_CODE
        else -> BIG_PENDING_CODE
    }

    fun decodeBig(value: Double): String = when {
        value <= BIG_SMOOTH_CODE + 0.5 -> IpoBigConsumption.SMOOTH
        value >= BIG_FAILED_CODE - 0.5 -> IpoBigConsumption.FAILED
        else -> IpoBigConsumption.PENDING
    }

    /** 지표 하나 → Room upsert용 엔티티. `source`는 이 테이블에서 항상 MANUAL이므로 컬럼에 담지 않는다. */
    fun toEntity(key: ManualIndicatorKey, value: Double, updatedAt: Long): BearSignalManualInputEntity =
        BearSignalManualInputEntity(indicatorKey = key.key, value = value, updatedAt = updatedAt)

    /**
     * Room 캐시 → 도메인 모델. 키가 하나도 없으면(수동 입력 전무) 전 필드가 null인
     * [ManualBearSignalInputs]를 반환한다 — "미설정"은 정상 상태이므로 컨테이너 자체는 null이 아니다.
     */
    fun toDomain(entities: List<BearSignalManualInputEntity>): ManualBearSignalInputs {
        val byKey = entities.associateBy { it.indicatorKey }
        return ManualBearSignalInputs(
            loss = byKey[ManualIndicatorKey.LOSS.key]?.let { toDoubleIndicator(it) },
            big = byKey[ManualIndicatorKey.BIG.key]?.let { AutoIndicator(decodeBig(it.value), InputSource.MANUAL, it.updatedAt) },
            issueRatio = byKey[ManualIndicatorKey.ISSUE_RATIO.key]?.let { toDoubleIndicator(it) },
            credit = byKey[ManualIndicatorKey.CREDIT.key]?.let { toDoubleIndicator(it) },
            margin = byKey[ManualIndicatorKey.MARGIN.key]?.let { AutoIndicator(decodeBoolean(it.value), InputSource.MANUAL, it.updatedAt) },
            dir = byKey[ManualIndicatorKey.DIR.key]?.let { AutoIndicator(decodeDir(it.value), InputSource.MANUAL, it.updatedAt) }
        )
    }

    private fun toDoubleIndicator(entity: BearSignalManualInputEntity): AutoIndicator<Double> =
        AutoIndicator(entity.value, InputSource.MANUAL, entity.updatedAt)
}
