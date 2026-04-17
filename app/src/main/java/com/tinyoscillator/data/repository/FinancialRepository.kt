package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.entity.FinancialCacheEntity
import com.tinyoscillator.data.dto.*
import com.tinyoscillator.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

class FinancialRepository(
    private val financialCacheDao: FinancialCacheDao,
    private val kisApiClient: KisApiClient,
    private val json: Json
) {
    private val lastCleanupTime = AtomicLong(0L)
    suspend fun getFinancialData(
        ticker: String,
        name: String,
        kisConfig: KisApiKeyConfig,
        useCache: Boolean = true
    ): Result<FinancialData> {
        try {
            if (ticker.isBlank()) {
                return Result.failure(IllegalArgumentException("종목코드가 비어있습니다."))
            }
            if (!kisConfig.isValid()) {
                return Result.failure(
                    IllegalStateException("KIS API key not configured. 설정에서 KIS API 키를 입력해주세요.")
                )
            }

            // Periodic cleanup: delete caches older than 7 days (throttled to once per hour)
            val now = System.currentTimeMillis()
            val lastCleanup = lastCleanupTime.get()
            if (now - lastCleanup > CLEANUP_INTERVAL_MS &&
                lastCleanupTime.compareAndSet(lastCleanup, now)) {
                financialCacheDao.deleteExpired(now - 7 * CACHE_TTL_MS)
            }

            // TTL-based cache-first: return cached data if within 24h
            var parsedCache: FinancialData? = null
            if (useCache) {
                val cachedEntity = financialCacheDao.get(ticker)
                if (cachedEntity != null) {
                    try {
                        parsedCache = json.decodeFromString<FinancialDataCache>(cachedEntity.data).toData()
                        if (!isCacheExpired(cachedEntity.cachedAt)) {
                            Timber.d("캐시에서 반환 (TTL 유효): %s", ticker)
                            return Result.success(parsedCache!!)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "캐시 역직렬화 실패, API에서 재조회: %s", ticker)
                        financialCacheDao.delete(ticker)
                        parsedCache = null
                    }
                }
            }

            // Cache expired or useCache=false → fetch from API
            val existingCache = parsedCache ?: loadCachedData(ticker)
            val freshData = fetchFromApi(ticker, name, kisConfig)

            // Merge: existing cache + fresh API data
            val mergedData = if (existingCache != null && freshData != null) {
                Timber.i("재무 데이터 merge: %s (캐시 %d기간 + API %d기간)",
                    ticker, existingCache.periods.size, freshData.periods.size)
                mergeData(existingCache, freshData)
            } else if (freshData == null && existingCache != null) {
                Timber.w("API 수집 실패 → 기존 캐시 사용: %s (%d기간)", ticker, existingCache.periods.size)
                existingCache
            } else {
                freshData ?: existingCache
            }

            if (mergedData == null) {
                Timber.e("재무 데이터 수집 완전 실패: %s (캐시 없음, API 실패)", ticker)
                return Result.failure(Exception("재무정보를 가져올 수 없습니다."))
            }

            // Save to DB
            saveToCacheDb(mergedData)

            return Result.success(mergedData)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to get financial data for %s", ticker)
            // Fallback to stale cache if API fails
            val cached = loadCachedData(ticker)
            if (cached != null) {
                Timber.d("API 실패 → stale 캐시 반환: %s", ticker)
                return Result.success(cached)
            }
            return Result.failure(e)
        }
    }

    private fun isCacheExpired(cachedAt: Long): Boolean {
        return System.currentTimeMillis() - cachedAt > CACHE_TTL_MS
    }

    suspend fun refreshFinancialData(
        ticker: String,
        name: String,
        kisConfig: KisApiKeyConfig
    ): Result<FinancialData> {
        try {
            if (ticker.isBlank()) {
                return Result.failure(IllegalArgumentException("종목코드가 비어있습니다."))
            }
            if (!kisConfig.isValid()) {
                return Result.failure(
                    IllegalStateException("KIS API key not configured. 설정에서 KIS API 키를 입력해주세요.")
                )
            }

            val freshData = fetchFromApi(ticker, name, kisConfig)
                ?: return Result.failure(Exception("재무정보를 가져올 수 없습니다."))

            // Merge with existing cache for incremental update
            val existingCache = loadCachedData(ticker)
            val mergedData = if (existingCache != null) {
                mergeData(existingCache, freshData)
            } else {
                freshData
            }

            saveToCacheDb(mergedData)
            return Result.success(mergedData)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to refresh financial data for %s", ticker)
            // Fallback to stale cache if API fails
            val cached = loadCachedData(ticker)
            if (cached != null) {
                Timber.d("Refresh 실패 → stale 캐시 반환: %s", ticker)
                return Result.success(cached)
            }
            return Result.failure(e)
        }
    }

    // ========== Private Helpers ==========

    private suspend fun loadCachedData(ticker: String): FinancialData? {
        return try {
            val cached = financialCacheDao.get(ticker) ?: return null
            val cacheData = json.decodeFromString<FinancialDataCache>(cached.data)
            cacheData.toData()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse cached data for %s", ticker)
            financialCacheDao.delete(ticker)
            null
        }
    }

    private suspend fun saveToCacheDb(data: FinancialData) {
        val entity = FinancialCacheEntity(
            ticker = data.ticker,
            name = data.name,
            data = json.encodeToString(FinancialDataCache.serializer(), data.toCache())
        )
        financialCacheDao.insert(entity)
    }

    /**
     * 5개 재무제표(BS/IS/수익성/안정성/성장성) 병렬 수집.
     *
     * `executeRequest`로 감싸 5개 호출이 일시 장애로 동시 실패해도
     * 서킷 브레이커에는 단일 실패만 기록되도록 한다.
     */
    private suspend fun fetchFromApi(
        ticker: String,
        name: String,
        kisConfig: KisApiKeyConfig
    ): FinancialData? {
        return try {
            val queryParams = mapOf(
                "FID_DIV_CLS_CODE" to "1",
                "FID_COND_MRKT_DIV_CODE" to "J",
                "FID_INPUT_ISCD" to ticker
            )

            val fetchResults = kisApiClient.executeRequest {
                withTimeout(API_BATCH_TIMEOUT_MS) {
                    coroutineScope {
                        val bs = async {
                            fetchList(TR_BALANCE_SHEET, EP_BALANCE_SHEET, queryParams, kisConfig) { mapToBalanceSheet(it) }
                        }
                        val is_ = async {
                            fetchList(TR_INCOME_STATEMENT, EP_INCOME_STATEMENT, queryParams, kisConfig) { mapToIncomeStatement(it) }
                        }
                        val pr = async {
                            fetchList(TR_PROFITABILITY, EP_PROFITABILITY, queryParams, kisConfig) { mapToProfitabilityRatios(it) }
                        }
                        val sr = async {
                            fetchList(TR_STABILITY, EP_STABILITY, queryParams, kisConfig) { mapToStabilityRatios(it) }
                        }
                        val gr = async {
                            fetchList(TR_GROWTH, EP_GROWTH, queryParams, kisConfig) { mapToGrowthRatios(it) }
                        }
                        FetchResults(bs.await(), is_.await(), pr.await(), sr.await(), gr.await())
                    }
                }
            }.getOrThrow()
            val (balanceSheets, incomeStatements, profitRatios, stabilityRatios, growthRatios) = fetchResults

            val results = listOf(
                "BalanceSheet" to balanceSheets.isNotEmpty(),
                "IncomeStatement" to incomeStatements.isNotEmpty(),
                "Profitability" to profitRatios.isNotEmpty(),
                "Stability" to stabilityRatios.isNotEmpty(),
                "Growth" to growthRatios.isNotEmpty()
            )
            val successCount = results.count { it.second }
            if (successCount < results.size) {
                val failed = results.filter { !it.second }.joinToString { it.first }
                Timber.w("Partial API fetch for %s: %d/%d succeeded, failed: %s",
                    ticker, successCount, results.size, failed)
            }

            buildFinancialData(ticker, name, balanceSheets, incomeStatements, profitRatios, stabilityRatios, growthRatios)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to fetch API data for %s", ticker)
            null
        }
    }

    private suspend fun <T> fetchList(
        trId: String,
        endpoint: String,
        queryParams: Map<String, String>,
        kisConfig: KisApiKeyConfig,
        mapper: (Map<String, String?>) -> T?
    ): List<T> {
        return try {
            val result = kisApiClient.get(
                trId = trId,
                url = endpoint,
                queryParams = queryParams,
                config = kisConfig
            ) { responseBody ->
                val apiResponse = json.decodeFromString<KisFinancialApiResponse>(responseBody)
                if (apiResponse.rtCd != "0") {
                    Timber.w("API error for %s: %s - %s", trId, apiResponse.msgCd, apiResponse.msg1)
                    return@get emptyList<T>()
                }
                val output = apiResponse.actualOutput ?: return@get emptyList<T>()
                output.mapNotNull { mapper(it) }
            }
            result.getOrElse {
                Timber.w("Failed to fetch %s: %s", trId, it.message)
                emptyList()
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("Exception fetching %s: %s", trId, e.message)
            emptyList()
        }
    }

    private fun buildFinancialData(
        ticker: String,
        name: String,
        balanceSheets: List<BalanceSheet>,
        incomeStatements: List<IncomeStatement>,
        profitRatios: List<ProfitabilityRatios>,
        stabilityRatios: List<StabilityRatios>,
        growthRatios: List<GrowthRatios>
    ): FinancialData {
        val allPeriods = mutableSetOf<String>()
        balanceSheets.forEach { allPeriods.add(it.period.yearMonth) }
        incomeStatements.forEach { allPeriods.add(it.period.yearMonth) }
        profitRatios.forEach { allPeriods.add(it.period.yearMonth) }
        stabilityRatios.forEach { allPeriods.add(it.period.yearMonth) }
        growthRatios.forEach { allPeriods.add(it.period.yearMonth) }

        return FinancialData(
            ticker = ticker,
            name = name,
            periods = allPeriods.sorted(),
            balanceSheets = balanceSheets.associateBy { it.period.yearMonth },
            incomeStatements = incomeStatements.associateBy { it.period.yearMonth },
            profitabilityRatios = profitRatios.associateBy { it.period.yearMonth },
            stabilityRatios = stabilityRatios.associateBy { it.period.yearMonth },
            growthRatios = growthRatios.associateBy { it.period.yearMonth }
        )
    }

    private fun mergeData(existing: FinancialData, fresh: FinancialData): FinancialData {
        // fresh overrides existing for same periods (Kotlin map + gives right-side priority)
        val overwrittenPeriods = existing.balanceSheets.keys.intersect(fresh.balanceSheets.keys) +
                existing.incomeStatements.keys.intersect(fresh.incomeStatements.keys)
        if (overwrittenPeriods.isNotEmpty()) {
            Timber.d("mergeData: %d기간 업데이트 (fresh 우선): %s", overwrittenPeriods.size, overwrittenPeriods.take(3))
        }

        val mergedBalanceSheets = existing.balanceSheets + fresh.balanceSheets
        val mergedIncomeStatements = existing.incomeStatements + fresh.incomeStatements
        val mergedProfitabilityRatios = existing.profitabilityRatios + fresh.profitabilityRatios
        val mergedStabilityRatios = existing.stabilityRatios + fresh.stabilityRatios
        val mergedGrowthRatios = existing.growthRatios + fresh.growthRatios

        val mergedPeriods = (mergedBalanceSheets.keys + mergedIncomeStatements.keys +
                mergedProfitabilityRatios.keys + mergedStabilityRatios.keys +
                mergedGrowthRatios.keys).distinct().sorted()

        return FinancialData(
            ticker = fresh.ticker,
            name = fresh.name,
            periods = mergedPeriods,
            balanceSheets = mergedBalanceSheets,
            incomeStatements = mergedIncomeStatements,
            profitabilityRatios = mergedProfitabilityRatios,
            stabilityRatios = mergedStabilityRatios,
            growthRatios = mergedGrowthRatios
        )
    }

    private data class FetchResults(
        val balanceSheets: List<BalanceSheet>,
        val incomeStatements: List<IncomeStatement>,
        val profitabilityRatios: List<ProfitabilityRatios>,
        val stabilityRatios: List<StabilityRatios>,
        val growthRatios: List<GrowthRatios>
    )

    companion object {
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L  // 24시간
        private const val CLEANUP_INTERVAL_MS = 60 * 60 * 1000L  // 1시간
        private const val API_BATCH_TIMEOUT_MS = 180_000L  // 180초 (5 parallel API calls, 각 ~30초 여유)

        private const val TR_BALANCE_SHEET = "FHKST66430100"
        private const val TR_INCOME_STATEMENT = "FHKST66430200"
        private const val TR_PROFITABILITY = "FHKST66430400"
        private const val TR_STABILITY = "FHKST66430600"
        private const val TR_GROWTH = "FHKST66430800"

        private const val EP_BALANCE_SHEET = "/uapi/domestic-stock/v1/finance/balance-sheet"
        private const val EP_INCOME_STATEMENT = "/uapi/domestic-stock/v1/finance/income-statement"
        private const val EP_PROFITABILITY = "/uapi/domestic-stock/v1/finance/profit-ratio"
        private const val EP_STABILITY = "/uapi/domestic-stock/v1/finance/stability-ratio"
        private const val EP_GROWTH = "/uapi/domestic-stock/v1/finance/growth-ratio"
    }
}
