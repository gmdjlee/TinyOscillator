package com.tinyoscillator.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Feature Store 도메인 모델
 */

/** 캐시 키 — ticker + featureName + date 조합으로 유일성 보장 */
data class FeatureKey(
    val ticker: String,
    val featureName: String,
    val date: LocalDate = LocalDate.now()
) {
    fun asString(): String =
        "$ticker:$featureName:${date.format(DateTimeFormatter.BASIC_ISO_DATE)}"
}

/** TTL 정책 */
sealed class FeatureTtl {
    abstract val ms: Long

    /** 장중 실시간 — 15분 */
    data object Intraday : FeatureTtl() {
        override val ms: Long = 15L * 60 * 1000
    }

    /** 일간 — 4시간 */
    data object Daily : FeatureTtl() {
        override val ms: Long = 4L * 60 * 60 * 1000
    }

    /** 주간 — 24시간 */
    data object Weekly : FeatureTtl() {
        override val ms: Long = 24L * 60 * 60 * 1000
    }

    /** 사용자 정의 */
    data class Custom(override val ms: Long) : FeatureTtl()
}

/** 캐시 통계 */
data class CacheStats(
    val hitCount: Long = 0,
    val missCount: Long = 0,
    val entryCount: Int = 0
)
