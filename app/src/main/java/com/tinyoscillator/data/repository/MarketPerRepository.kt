package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.MarketPerDao
import com.tinyoscillator.core.database.entity.MarketPerEntity
import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.domain.model.MarketPerChartData
import com.tinyoscillator.domain.model.MarketPerRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketPerRepository @Inject constructor(
    private val marketPerDao: MarketPerDao,
    private val krxApiClient: KrxApiClient
) {
    private val fmt = DateFormats.yyyyMMdd
    private val lastFetchTime = ConcurrentHashMap<String, Long>()

    companion object {
        private const val COOLDOWN_MS = 3_600_000L // 1시간
        private const val TTL_DAYS = 730L
        private const val MAX_COOLDOWN_ENTRIES = 10

        /** 지수 티커 매핑 */
        private val MARKET_TICKER = mapOf(
            "KOSPI" to "1001",
            "KOSDAQ" to "2001"
        )
    }

    suspend fun getMarketPerHistory(
        market: String,
        startDate: String,
        endDate: String,
        krxId: String,
        krxPassword: String
    ): MarketPerChartData = withContext(Dispatchers.IO) {
        val latestCachedDate = marketPerDao.getLatestDate(market)
        val today = LocalDate.now().format(fmt)

        // 캐시가 오늘까지 있음 → DB에서만 반환
        if (latestCachedDate != null && latestCachedDate >= today) {
            Timber.d("MarketPER 캐시 최신 ($latestCachedDate >= $today) → DB 반환")
            return@withContext loadFromCache(market, startDate, endDate)
        }

        // 주말/공휴일 반복 호출 방지: 1시간 쿨다운
        if (latestCachedDate != null) {
            val lastFetch = lastFetchTime[market] ?: 0L
            if (System.currentTimeMillis() - lastFetch < COOLDOWN_MS) {
                Timber.d("MarketPER 쿨다운 중 → 캐시 반환: $market")
                return@withContext loadFromCache(market, startDate, endDate)
            }
        }

        // KRX 로그인
        val loggedIn = krxApiClient.login(krxId, krxPassword)
        if (!loggedIn) {
            Timber.w("KRX 로그인 실패 → 캐시 반환")
            return@withContext loadFromCache(market, startDate, endDate)
        }

        val krxIndex = krxApiClient.getKrxIndex()
        if (krxIndex == null) {
            Timber.w("KRX Index 클라이언트 없음 → 캐시 반환")
            return@withContext loadFromCache(market, startDate, endDate)
        }

        val ticker = MARKET_TICKER[market]
        if (ticker == null) {
            Timber.w("알 수 없는 시장: $market")
            return@withContext loadFromCache(market, startDate, endDate)
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
            return@withContext loadFromCache(market, startDate, endDate)
        }

        Timber.d("MarketPER: incremental fetch $fetchStartDate ~ $endDate (market=$market)")

        val newData = try {
            krxIndex.getIndexFundamental(fetchStartDate, endDate, ticker)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "MarketPER: API 호출 실패, 캐시 반환")
            return@withContext loadFromCache(market, startDate, endDate)
        }

        lastFetchTime[market] = System.currentTimeMillis()
        if (lastFetchTime.size > MAX_COOLDOWN_ENTRIES) {
            val oldest = lastFetchTime.entries.minByOrNull { it.value }?.key
            if (oldest != null) lastFetchTime.remove(oldest)
        }

        if (newData.isNotEmpty()) {
            // 당일 미확정 데이터(PER=0) 제외
            val entities = newData.filter { it.per > 0.0 }.map { item ->
                MarketPerEntity(
                    market = market,
                    date = item.date,
                    closeIndex = item.close,
                    per = item.per,
                    pbr = item.pbr,
                    dividendYield = item.dividendYield
                )
            }
            val cutoffDate = LocalDate.now().minusDays(TTL_DAYS).format(fmt)
            marketPerDao.insertAndCleanup(entities, market, cutoffDate)
            Timber.d("MarketPER ${newData.size}건 저장 (market=$market)")
        }

        loadFromCache(market, startDate, endDate)
    }

    private suspend fun loadFromCache(
        market: String,
        startDate: String,
        endDate: String
    ): MarketPerChartData {
        val rows = marketPerDao.getByMarketDateRange(market, startDate, endDate)
            .map { entity ->
                MarketPerRow(
                    date = entity.date,
                    closeIndex = entity.closeIndex,
                    per = entity.per,
                    pbr = entity.pbr,
                    dividendYield = entity.dividendYield
                )
            }
        return MarketPerChartData(market = market, rows = rows)
    }
}
