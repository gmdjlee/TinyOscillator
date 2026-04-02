package com.tinyoscillator.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Feature Store 캐시 엔트리
 *
 * 계산 비용이 높은 통계 엔진 결과를 TTL 기반으로 캐싱.
 * key 형식: "{ticker}:{featureName}:{dateYYYYMMDD}"
 */
@Entity(
    tableName = "feature_cache",
    indices = [
        Index(value = ["ticker"]),
        Index(value = ["computed_at"])
    ]
)
data class FeatureCacheEntity(
    @PrimaryKey
    val key: String,

    /** 종목코드 (evictByTicker 검색용) */
    val ticker: String,

    /** 피처 이름 (예: "NaiveBayes", "Logistic") */
    @ColumnInfo(name = "feature_name")
    val featureName: String,

    /** JSON 직렬화된 결과값 */
    val value: String,

    /** 계산 시각 (epoch millis) */
    @ColumnInfo(name = "computed_at")
    val computedAt: Long,

    /** TTL (밀리초) */
    @ColumnInfo(name = "ttl_ms")
    val ttlMs: Long
)
