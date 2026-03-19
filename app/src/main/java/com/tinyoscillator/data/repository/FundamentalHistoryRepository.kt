package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.FundamentalCacheDao
import com.tinyoscillator.core.database.entity.FundamentalCacheEntity
import com.tinyoscillator.domain.model.FundamentalHistoryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FundamentalHistoryRepository @Inject constructor(
    private val fundamentalCacheDao: FundamentalCacheDao,
    private val krxApiClient: KrxApiClient
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val lastFetchTime = ConcurrentHashMap<String, Long>()

    companion object {
        private const val COOLDOWN_MS = 3_600_000L // 1시간
        private const val TTL_DAYS = 730L
    }

    suspend fun getFundamentalHistory(
        ticker: String,
        startDate: String,
        endDate: String
    ): List<FundamentalHistoryData> = withContext(Dispatchers.IO) {
        val latestCachedDate = fundamentalCacheDao.getLatestDate(ticker)
        val today = LocalDate.now().format(fmt)

        // 캐시가 오늘까지 있음 → DB에서만 반환
        if (latestCachedDate != null && latestCachedDate >= today) {
            Timber.d("Fundamental 캐시 최신 ($latestCachedDate >= $today) → DB 반환")
            return@withContext loadFromCache(ticker, startDate, endDate)
        }

        // 주말/공휴일 반복 호출 방지: 1시간 쿨다운
        if (latestCachedDate != null) {
            val lastFetch = lastFetchTime[ticker] ?: 0L
            if (System.currentTimeMillis() - lastFetch < COOLDOWN_MS) {
                Timber.d("Fundamental 쿨다운 중 → 캐시 반환: $ticker")
                return@withContext loadFromCache(ticker, startDate, endDate)
            }
        }

        val krxStock = krxApiClient.getKrxStock()
        Timber.d("FundamentalRepo: krxStock=${if (krxStock != null) "available" else "NULL"}")
        if (krxStock == null) {
            Timber.w("KRX 미로그인 → 캐시 반환")
            return@withContext loadFromCache(ticker, startDate, endDate)
        }

        // 증분 fetch
        val fetchStartDate = if (latestCachedDate != null) {
            val nextDay = LocalDate.parse(latestCachedDate, fmt).plusDays(1)
            nextDay.format(fmt)
        } else {
            startDate
        }

        if (fetchStartDate > endDate) {
            Timber.d("Fetch 범위 없음 ($fetchStartDate > $endDate) → 캐시 반환")
            return@withContext loadFromCache(ticker, startDate, endDate)
        }

        Timber.d("FundamentalRepo: incremental fetch $fetchStartDate ~ $endDate (ticker=$ticker)")

        val newData = try {
            val result = krxStock.getFundamentalByTicker(fetchStartDate, endDate, ticker)
            Timber.d("FundamentalRepo: API returned ${result.size} items")
            result
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "FundamentalRepo: API 호출 실패, 캐시 반환")
            return@withContext loadFromCache(ticker, startDate, endDate)
        }

        lastFetchTime[ticker] = System.currentTimeMillis()

        if (newData.isNotEmpty()) {
            val entities = newData.map { item ->
                FundamentalCacheEntity(
                    ticker = ticker,
                    date = item.date,
                    close = item.close,
                    eps = item.eps,
                    per = item.per,
                    bps = item.bps,
                    pbr = item.pbr,
                    dps = item.dps,
                    dividendYield = item.dividendYield
                )
            }
            val cutoffDate = LocalDate.now().minusDays(TTL_DAYS).format(fmt)
            fundamentalCacheDao.insertAndCleanup(entities, ticker, cutoffDate)
            Timber.d("Fundamental ${newData.size}건 저장 (ticker=$ticker)")
        }

        loadFromCache(ticker, startDate, endDate)
    }

    private suspend fun loadFromCache(
        ticker: String,
        startDate: String,
        endDate: String
    ): List<FundamentalHistoryData> {
        return fundamentalCacheDao.getByTickerDateRange(ticker, startDate, endDate)
            .map { entity ->
                FundamentalHistoryData(
                    date = entity.date,
                    close = entity.close,
                    eps = entity.eps,
                    per = entity.per,
                    bps = entity.bps,
                    pbr = entity.pbr,
                    dps = entity.dps,
                    dividendYield = entity.dividendYield
                )
            }
    }
}
