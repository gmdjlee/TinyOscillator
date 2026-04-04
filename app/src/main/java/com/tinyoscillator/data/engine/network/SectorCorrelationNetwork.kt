package com.tinyoscillator.data.engine.network

import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.domain.model.DailyTrading
import com.tinyoscillator.domain.model.SectorCorrelationResult
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 섹터 상관관계 네트워크 엔진 (11번째 통계 엔진)
 *
 * Ledoit-Wolf 축소 추정량으로 섹터 내 종목 간 상관 행렬을 산출하고,
 * 그래프 기반 이상치 탐지로 섹터에서 이탈한 종목을 감지.
 *
 * 상관 붕괴(아웃라이어) = 잠재적 추세 전환 선행 신호.
 *
 * 주간 갱신 주기 권장 (인트라데이 재계산 불필요).
 */
@Singleton
class SectorCorrelationNetwork @Inject constructor(
    private val stockMasterDao: StockMasterDao,
    private val analysisCacheDao: AnalysisCacheDao
) {

    companion object {
        /** 상관 행렬 산출용 윈도우 (거래일) */
        const val DEFAULT_WINDOW = 60
        /** 그래프 엣지 임계값 (|상관| >= threshold면 연결) */
        const val DEFAULT_EDGE_THRESHOLD = 0.5
        /** 아웃라이어 판별 배수 (edge_threshold * factor) */
        const val OUTLIER_FACTOR = 0.6
        /** 섹터당 최대 종목 수 (성능 제약) */
        const val MAX_PEERS = 30
        /** 최소 피어 수 (이보다 적으면 분석 불가) */
        const val MIN_PEERS = 5
        /** 최소 공통 거래일 */
        const val MIN_COMMON_DAYS = 40
    }

    /**
     * 섹터 상관 네트워크 기반 신호 생성
     *
     * @param prices 대상 종목의 일별 가격 데이터
     * @param stockCode 대상 종목 코드
     * @return SectorCorrelationResult
     */
    suspend fun analyze(
        prices: List<DailyTrading>,
        stockCode: String,
        window: Int = DEFAULT_WINDOW,
        edgeThreshold: Double = DEFAULT_EDGE_THRESHOLD
    ): SectorCorrelationResult {
        // 1. 섹터 조회
        val sector = stockMasterDao.getSector(stockCode)
        if (sector.isNullOrBlank()) {
            return unavailable(stockCode, "섹터 정보 없음")
        }

        // 2. 섹터 피어 조회
        val sectorPeers = stockMasterDao.getTickersBySector(sector, MAX_PEERS + 1)
            .filter { it != stockCode }
            .take(MAX_PEERS)

        if (sectorPeers.size < MIN_PEERS) {
            return unavailable(stockCode, "섹터 피어 부족 (${sectorPeers.size}개 < $MIN_PEERS)", sector)
        }

        // 3. 대상 종목 일간 수익률
        val targetCloses = prices.filter { it.closePrice > 0 }
            .takeLast(window + 1)
        if (targetCloses.size < MIN_COMMON_DAYS + 1) {
            return unavailable(stockCode, "대상 종목 가격 데이터 부족", sector)
        }

        val targetDates = targetCloses.map { it.date }.toSet()
        val targetReturns = computeReturns(targetCloses.map { it.closePrice.toDouble() })

        // 4. 피어 종목 수익률 수집 (캐시된 데이터 사용)
        val startDate = targetCloses.first().date
        val endDate = targetCloses.last().date
        val peerReturnsMap = mutableMapOf<String, DoubleArray>()

        for (peer in sectorPeers) {
            val cached = analysisCacheDao.getByTickerDateRange(peer, startDate, endDate)
            if (cached.size < MIN_COMMON_DAYS) continue

            // 공통 날짜로 정렬
            val peerCloses = cached.filter { it.closePrice > 0 && it.date in targetDates }
                .sortedBy { it.date }
                .map { it.closePrice.toDouble() }

            if (peerCloses.size >= MIN_COMMON_DAYS) {
                peerReturnsMap[peer] = computeReturns(peerCloses)
            }
        }

        if (peerReturnsMap.size < MIN_PEERS) {
            return unavailable(stockCode, "캐시된 피어 데이터 부족 (${peerReturnsMap.size}개)", sector)
        }

        Timber.d("섹터 상관 분석: %s 섹터 %s, 피어 %d개, 윈도우 %d일",
            stockCode, sector, peerReturnsMap.size, window)

        // 5. 수익률 행렬 구성 (대상 종목 + 피어)
        // 모든 종목의 수익률 길이를 최소 공통 길이로 맞춤
        val allTickers = listOf(stockCode) + peerReturnsMap.keys.toList()
        val allReturns = mutableListOf(targetReturns)
        allReturns.addAll(peerReturnsMap.values)

        val minLen = allReturns.minOf { it.size }
        if (minLen < MIN_COMMON_DAYS) {
            return unavailable(stockCode, "공통 거래일 부족 ($minLen)", sector)
        }

        // 마지막 minLen일만 사용
        val returnsMatrix = Array(allTickers.size) { i ->
            val r = allReturns[i]
            r.copyOfRange(r.size - minLen, r.size)
        }

        // 6. Ledoit-Wolf 축소 상관 행렬
        val (corrMatrix, shrinkage) = ledoitWolfCorrelation(returnsMatrix)

        // 7. 그래프 구축 + 아웃라이어 탐지
        val targetIdx = 0  // 대상 종목은 행렬의 첫 번째
        val neighbors = mutableListOf<Int>()
        for (j in corrMatrix.indices) {
            if (j != targetIdx && abs(corrMatrix[targetIdx][j]) >= edgeThreshold) {
                neighbors.add(j)
            }
        }

        val meanNeighborCorr = if (neighbors.isNotEmpty()) {
            neighbors.sumOf { corrMatrix[targetIdx][it] } / neighbors.size
        } else 0.0

        val isOutlier = meanNeighborCorr < edgeThreshold * OUTLIER_FACTOR

        // 전체 네트워크 밀도
        var totalAbsCorr = 0.0
        var corrCount = 0
        for (i in corrMatrix.indices) {
            for (j in i + 1 until corrMatrix.size) {
                totalAbsCorr += abs(corrMatrix[i][j])
                corrCount++
            }
        }
        val avgAbsCorr = if (corrCount > 0) totalAbsCorr / corrCount else 0.0

        // 대상 종목의 상관 순위 (낮은 평균 상관 = 낮은 순위)
        val meanCorrs = corrMatrix.indices.map { i ->
            val others = corrMatrix.indices.filter { it != i }
            if (others.isEmpty()) 0.0
            else others.sumOf { corrMatrix[i][it] } / others.size
        }
        val sortedCorrs = meanCorrs.sortedBy { it }
        val corrRank = sortedCorrs.indexOf(meanCorrs[targetIdx]) + 1

        // 신호 점수: 아웃라이어면 1.0 (전환 가능성), 정상이면 0.5
        val signalScore = if (isOutlier) {
            // 이탈 정도에 따라 0.6~1.0
            val outlierDegree = 1.0 - (meanNeighborCorr / (edgeThreshold * OUTLIER_FACTOR))
                .coerceIn(0.0, 1.0)
            (0.6 + 0.4 * outlierDegree).coerceIn(0.0, 1.0)
        } else {
            // 정상: 상관 강도에 따라 0.3~0.5
            val normalDegree = (meanNeighborCorr / edgeThreshold).coerceIn(0.0, 1.0)
            (0.3 + 0.2 * normalDegree).coerceIn(0.0, 1.0)
        }

        return SectorCorrelationResult(
            isOutlier = isOutlier,
            meanNeighborCorr = meanNeighborCorr,
            nNeighbors = neighbors.size,
            signalScore = signalScore,
            sectorName = sector,
            nPeers = peerReturnsMap.size,
            shrinkageIntensity = shrinkage,
            avgAbsCorr = avgAbsCorr,
            corrRank = corrRank
        )
    }

    // ─── Ledoit-Wolf 축소 추정량 ───

    /**
     * Ledoit-Wolf 축소 상관 행렬 산출
     *
     * 공분산 → 축소 공분산 → 상관 행렬 변환.
     * 축소 타겟: 대각 행렬 (자기 분산만 유지).
     *
     * @param returns (n_tickers x n_days) 수익률 행렬
     * @return Pair(상관 행렬, 축소 강도)
     */
    internal fun ledoitWolfCorrelation(returns: Array<DoubleArray>): Pair<Array<DoubleArray>, Double> {
        val p = returns.size       // 종목 수
        val n = returns[0].size    // 관측치 수

        // 평균 제거
        val means = DoubleArray(p) { i -> returns[i].average() }
        val centered = Array(p) { i ->
            DoubleArray(n) { j -> returns[i][j] - means[i] }
        }

        // 표본 공분산 행렬 S = X * X' / (n-1)
        val sampleCov = Array(p) { DoubleArray(p) }
        for (i in 0 until p) {
            for (j in i until p) {
                var sum = 0.0
                for (k in 0 until n) {
                    sum += centered[i][k] * centered[j][k]
                }
                val cov = sum / (n - 1)
                sampleCov[i][j] = cov
                sampleCov[j][i] = cov
            }
        }

        // 축소 타겟 F = diag(S) * I (대각 원소만 유지)
        val target = Array(p) { i -> DoubleArray(p) { j -> if (i == j) sampleCov[i][i] else 0.0 } }

        // 최적 축소 강도 계산 (Ledoit & Wolf 2004 분석적 공식)
        val shrinkage = computeOptimalShrinkage(centered, sampleCov, target, n, p)

        // 축소 공분산: Σ = α * F + (1 - α) * S
        val shrunkCov = Array(p) { i ->
            DoubleArray(p) { j ->
                shrinkage * target[i][j] + (1.0 - shrinkage) * sampleCov[i][j]
            }
        }

        // 공분산 → 상관 행렬
        val corrMatrix = covToCorr(shrunkCov)

        return Pair(corrMatrix, shrinkage)
    }

    /**
     * Ledoit-Wolf 최적 축소 강도 (분석적 공식)
     *
     * Oracle Approximating Shrinkage (OAS) 변형.
     */
    private fun computeOptimalShrinkage(
        centered: Array<DoubleArray>,
        sampleCov: Array<DoubleArray>,
        target: Array<DoubleArray>,
        n: Int, p: Int
    ): Double {
        // ρ̂ 추정: sum of squared off-diagonal elements
        var sumSquaredDiff = 0.0
        for (i in 0 until p) {
            for (j in 0 until p) {
                val diff = sampleCov[i][j] - target[i][j]
                sumSquaredDiff += diff * diff
            }
        }

        // δ̂² = ||S - F||² / p²
        val deltaSq = sumSquaredDiff / (p.toDouble() * p)

        // β̂ 추정 (표본 공분산의 분산)
        var betaSum = 0.0
        for (k in 0 until n) {
            var innerSum = 0.0
            for (i in 0 until p) {
                for (j in 0 until p) {
                    val xij = centered[i][k] * centered[j][k] - sampleCov[i][j]
                    innerSum += xij * xij
                }
            }
            betaSum += innerSum
        }
        val betaHat = betaSum / (n.toDouble() * n * p * p)

        // α* = min(β̂ / δ̂², 1)
        return if (deltaSq > 0) min(betaHat / deltaSq, 1.0).coerceIn(0.0, 1.0) else 1.0
    }

    // ─── 유틸리티 ───

    /**
     * 공분산 행렬 → 상관 행렬 변환
     */
    private fun covToCorr(cov: Array<DoubleArray>): Array<DoubleArray> {
        val p = cov.size
        val stds = DoubleArray(p) { sqrt(max(cov[it][it], 1e-12)) }
        return Array(p) { i ->
            DoubleArray(p) { j ->
                if (i == j) 1.0
                else (cov[i][j] / (stds[i] * stds[j])).coerceIn(-1.0, 1.0)
            }
        }
    }

    /**
     * 가격 → 일간 수익률 변환
     */
    private fun computeReturns(closes: List<Double>): DoubleArray {
        if (closes.size < 2) return doubleArrayOf()
        return DoubleArray(closes.size - 1) { i ->
            if (closes[i] == 0.0) 0.0
            else (closes[i + 1] - closes[i]) / closes[i]
        }
    }

    /**
     * Overload: DoubleArray input
     */
    internal fun computeReturns(closes: DoubleArray): DoubleArray {
        if (closes.size < 2) return doubleArrayOf()
        return DoubleArray(closes.size - 1) { i ->
            if (closes[i] == 0.0) 0.0
            else (closes[i + 1] - closes[i]) / closes[i]
        }
    }

    private fun unavailable(
        stockCode: String,
        reason: String,
        sector: String = ""
    ): SectorCorrelationResult {
        Timber.w("섹터 상관 분석 불가 (%s): %s", stockCode, reason)
        return SectorCorrelationResult(
            isOutlier = false,
            meanNeighborCorr = 0.0,
            nNeighbors = 0,
            signalScore = 0.5,
            sectorName = sector,
            nPeers = 0,
            shrinkageIntensity = 0.0,
            avgAbsCorr = 0.0,
            unavailableReason = reason
        )
    }
}
