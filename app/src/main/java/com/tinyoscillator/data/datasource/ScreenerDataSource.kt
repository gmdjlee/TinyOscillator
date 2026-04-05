package com.tinyoscillator.data.datasource

import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.domain.model.ScreenerFilter
import com.tinyoscillator.domain.model.ScreenerResultItem
import com.tinyoscillator.domain.model.ScreenerSortKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 스크리너 데이터 수집 — Room DB에서 종목 후보 → 지표 조회 → 필터/정렬.
 *
 * 데이터 소스:
 * - stock_master: 종목 마스터 (시장, 섹터)
 * - analysis_cache: 시가총액, 외국인 순매수 (최근 1건)
 * - fundamental_cache: PBR (최근 1건)
 * - signal_history: 앙상블 신호 점수 (최근 평균)
 */
@Singleton
class ScreenerDataSource @Inject constructor(
    private val masterDao: StockMasterDao,
    private val analysisCacheDao: AnalysisCacheDao,
    private val fundamentalCacheDao: FundamentalCacheDao,
    private val calibrationDao: CalibrationDao,
) {
    suspend fun runScreener(
        filter: ScreenerFilter,
        sort: ScreenerSortKey = ScreenerSortKey.SIGNAL_SCORE,
        limit: Int = 50,
    ): List<ScreenerResultItem> = withContext(Dispatchers.IO) {

        // 1단계: Room에서 마스터 필터링 (시장·섹터)
        val candidates = masterDao.getFilteredCandidates(
            marketType = filter.marketType?.name,
            sectorCode = filter.sectorCode,
            candidateLimit = 500,
        )
        if (candidates.isEmpty()) return@withContext emptyList()

        // 2단계: 신호 점수 일괄 조회
        val signalMap = calibrationDao.getLatestAvgScoresByTicker()
            .associate { it.ticker to it.avgScore.toFloat() }

        // 3단계: 후보별 지표 수집 + 필터 적용
        val results = candidates.mapNotNull { master ->
            val signal = signalMap[master.ticker] ?: 0.5f

            // 시가총액 (analysis_cache 최근 1건, 억원 단위로 변환)
            val recentCache = analysisCacheDao.getRecentByTicker(master.ticker, 1).firstOrNull()
            val marketCapBil = (recentCache?.marketCap ?: 0L) / 100_000_000L

            // PBR (fundamental_cache 최근 1건)
            val fundLatest = fundamentalCacheDao.getLatestByTicker(master.ticker)
            val pbr = fundLatest?.pbr?.toFloat() ?: 0f

            // 외국인비중 근사: foreignNet / marketCap (최근 20일 평균)
            val recentData = analysisCacheDao.getRecentByTicker(master.ticker, 20)
            val foreignRatio = if (recentData.isNotEmpty()) {
                val avgForeignNet = recentData.map { it.foreignNet }.average()
                val avgMarketCap = recentData.map { it.marketCap }.average()
                if (avgMarketCap > 0) (avgForeignNet / avgMarketCap).toFloat().coerceIn(0f, 1f) else 0f
            } else 0f

            // 거래량 비율: 최근 1일 / 최근 20일 평균 (analysis_cache에 거래량 없으므로 1.0)
            val volumeRatio = 1.0f

            val item = ScreenerResultItem(
                ticker = master.ticker,
                name = master.name,
                signalScore = signal,
                pbr = pbr,
                marketCapBil = marketCapBil,
                foreignRatio = foreignRatio,
                volumeRatio = volumeRatio,
                sectorName = master.sector,
            )

            if (meetsFilter(item, filter)) item else null
        }

        // 4단계: 정렬 + 결과 제한
        results.sortedByDescending { it.sortValue(sort) }.take(limit)
    }

    companion object {
        fun meetsFilter(item: ScreenerResultItem, f: ScreenerFilter): Boolean =
            item.signalScore in f.minSignalScore..f.maxSignalScore &&
            item.marketCapBil in f.minMarketCapBil..f.maxMarketCapBil &&
            item.pbr in f.minPbr..f.maxPbr &&
            item.foreignRatio in f.minForeignRatio..f.maxForeignRatio &&
            item.volumeRatio >= f.minVolumeRatio

        fun ScreenerResultItem.sortValue(key: ScreenerSortKey): Float = when (key) {
            ScreenerSortKey.SIGNAL_SCORE -> signalScore
            ScreenerSortKey.MARKET_CAP -> marketCapBil.toFloat()
            ScreenerSortKey.PBR -> -pbr
            ScreenerSortKey.FOREIGN_RATIO -> foreignRatio
            ScreenerSortKey.VOLUME_RATIO -> volumeRatio
        }
    }
}
