package com.tinyoscillator.core.scraper

import kotlin.math.ln
import kotlin.random.Random

/**
 * 스크래퍼 공통 유틸 — 페이지/요청 간 자연스러운 랜덤 딜레이를 생성한다.
 *
 * 각 스크래퍼별 정책(서버 부하, 봇 차단 방지)에 맞춰 분포를 선택한다:
 * - 고부하 사이트 + 긴 간격(8~16초) → [gammaRandomDelayMs] (평균에 몰리고 tail이 있음)
 * - 일반 사이트 + 짧은 간격(1~5초) → [uniformRandomDelayMs]
 */
object ScraperUtils {

    /**
     * 균등 분포 기반 랜덤 딜레이 (ms). 짧은 간격(수 초)에 적합.
     *
     * @param minMs 최소 대기 시간 (포함).
     * @param maxMs 최대 대기 시간 (배제). `minMs < maxMs` 필수.
     */
    fun uniformRandomDelayMs(minMs: Long, maxMs: Long): Long {
        require(minMs < maxMs) { "minMs($minMs) must be < maxMs($maxMs)" }
        return minMs + Random.nextLong(maxMs - minMs)
    }

    /**
     * 감마 분포(shape=2, 지수 분포 2개의 합) 기반 랜덤 딜레이 (ms).
     * 균등 분포보다 평균 근처에 몰리고 긴 꼬리를 가져 "사람 같은" 패턴을 만든다.
     * 긴 간격(수십 초)과 봇 차단 회피가 필요한 사이트에 적합.
     *
     * @param minMs 최소 대기 시간 (포함).
     * @param maxMs 최대 대기 시간 (포함). `minMs < maxMs` 필수.
     */
    fun gammaRandomDelayMs(minMs: Long, maxMs: Long): Long {
        require(minMs < maxMs) { "minMs($minMs) must be < maxMs($maxMs)" }
        // 간단한 감마 분포 근사: 지수 분포 2개의 합
        val u1 = -ln(1.0 - Random.nextDouble())
        val u2 = -ln(1.0 - Random.nextDouble())
        val gamma = u1 + u2 // shape=2, scale=1

        // 0~1 범위로 정규화 후 [minMs, maxMs] 사이로 매핑
        val normalized = (gamma / 6.0).coerceIn(0.0, 1.0)
        return minMs + ((maxMs - minMs) * normalized).toLong()
    }
}
