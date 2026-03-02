package com.tinyoscillator.data.repository

import android.util.Log
import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.database.dao.FinancialCacheDao
import com.tinyoscillator.core.database.entity.FinancialCacheEntity
import com.tinyoscillator.data.dto.*
import com.tinyoscillator.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

class FinancialRepository(
    private val financialCacheDao: FinancialCacheDao,
    private val kisApiClient: KisApiClient,
    private val json: Json
) {
    suspend fun getFinancialData(
        ticker: String,
        name: String,
        kisConfig: KisApiKeyConfig,
        useCache: Boolean = true
    ): Result<FinancialData> {
        try {
            if (!kisConfig.isValid()) {
                return Result.failure(
                    IllegalStateException("KIS API key not configured. 설정에서 KIS API 키를 입력해주세요.")
                )
            }

            // Periodic cleanup: delete caches older than 7 days
            financialCacheDao.deleteExpired(System.currentTimeMillis() - 7 * CACHE_TTL_MS)

            // TTL-based cache-first: return cached data if within 24h
            if (useCache) {
                val cachedEntity = financialCacheDao.get(ticker)
                if (cachedEntity != null && !isCacheExpired(cachedEntity.cachedAt)) {
                    Log.d(TAG, "캐시에서 반환 (TTL 유효): $ticker")
                    val data = json.decodeFromString<FinancialDataCache>(cachedEntity.data)
                    return Result.success(data.toData())
                }
            }

            // Cache expired or useCache=false → fetch from API
            val existingCache = loadCachedData(ticker)
            val freshData = fetchFromApi(ticker, name, kisConfig)

            // Merge: existing cache + fresh API data
            val mergedData = if (existingCache != null && freshData != null) {
                mergeData(existingCache, freshData)
            } else {
                freshData ?: existingCache
            }

            if (mergedData == null) {
                return Result.failure(Exception("재무정보를 가져올 수 없습니다."))
            }

            // Save to DB
            saveToCacheDb(mergedData)

            return Result.success(mergedData)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get financial data for $ticker", e)
            // Fallback to stale cache if API fails
            val cached = loadCachedData(ticker)
            if (cached != null) {
                Log.d(TAG, "API 실패 → stale 캐시 반환: $ticker")
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
            Log.e(TAG, "Failed to refresh financial data for $ticker", e)
            return Result.failure(e)
        }
    }

    // ========== Private Helpers ==========

    private suspend fun loadCachedData(ticker: String): FinancialData? {
        return try {
            val cached = financialCacheDao.get(ticker) ?: return null
            val cacheData = json.decodeFromString<FinancialDataCache>(cached.data)
            cacheData.toData()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse cached data for $ticker", e)
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

            val (balanceSheets, incomeStatements, profitRatios, stabilityRatios, growthRatios) =
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

            buildFinancialData(ticker, name, balanceSheets, incomeStatements, profitRatios, stabilityRatios, growthRatios)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch API data for $ticker", e)
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
                    Log.w(TAG, "API error for $trId: ${apiResponse.msgCd} - ${apiResponse.msg1}")
                    return@get emptyList<T>()
                }
                val output = apiResponse.actualOutput ?: return@get emptyList<T>()
                output.mapNotNull { mapper(it) }
            }
            result.getOrElse {
                Log.w(TAG, "Failed to fetch $trId: ${it.message}")
                emptyList()
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Exception fetching $trId: ${e.message}")
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
        val mergedPeriods = (existing.periods + fresh.periods).distinct().sorted()
        return FinancialData(
            ticker = fresh.ticker,
            name = fresh.name,
            periods = mergedPeriods,
            balanceSheets = existing.balanceSheets + fresh.balanceSheets,
            incomeStatements = existing.incomeStatements + fresh.incomeStatements,
            profitabilityRatios = existing.profitabilityRatios + fresh.profitabilityRatios,
            stabilityRatios = existing.stabilityRatios + fresh.stabilityRatios,
            growthRatios = existing.growthRatios + fresh.growthRatios
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
        private const val TAG = "FinancialRepo"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L  // 24시간

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
