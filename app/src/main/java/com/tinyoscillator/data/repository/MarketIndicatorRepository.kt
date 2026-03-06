package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.entity.MarketDepositEntity
import com.tinyoscillator.core.database.entity.MarketOscillatorEntity
import com.tinyoscillator.core.scraper.NaverFinanceScraper
import com.tinyoscillator.domain.model.MarketDeposit
import com.tinyoscillator.domain.model.MarketDepositChartData
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.usecase.MarketOscillatorCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 시장지표 통합 Repository
 * - 과매수/과매도 (MarketOscillator)
 * - 자금 동향 (MarketDeposit)
 */
class MarketIndicatorRepository(
    private val oscillatorDao: MarketOscillatorDao,
    private val depositDao: MarketDepositDao,
    private val calculator: MarketOscillatorCalculator,
    private val scraper: NaverFinanceScraper
) {

    companion object {
        private const val DEFAULT_KEEP_DAYS = 90
        private const val DATA_EXPIRY_HOURS = 12
    }

    // ===== 과매수/과매도 =====

    fun getMarketData(market: String): Flow<List<MarketOscillator>> =
        oscillatorDao.getMarketData(market)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    fun getDataByDateRange(market: String, startDate: String, endDate: String): Flow<List<MarketOscillator>> =
        oscillatorDao.getDataByDateRange(market, startDate, endDate)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    suspend fun getLatestData(market: String): MarketOscillator? =
        withContext(Dispatchers.IO) {
            oscillatorDao.getLatestData(market)?.toDomain()
        }

    suspend fun getDataCount(market: String): Int =
        withContext(Dispatchers.IO) {
            oscillatorDao.getDataCount(market)
        }

    /**
     * 초기 데이터 수집
     */
    suspend fun initializeMarketData(
        market: String,
        days: Int,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Initializing $market data for $days days")
            onProgress?.invoke("$market 데이터 수집 준비 중...", 0)

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong())
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

            onProgress?.invoke("$market 시장 지수 데이터 수집 중...", 30)
            val result = calculator.analyze(market, startDate.format(formatter), endDate.format(formatter))

            if (result == null) {
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            onProgress?.invoke("$market 데이터 처리 중...", 70)

            if (result.dates.isEmpty()) {
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            // Entity 리스트 생성 (yyyyMMdd → yyyy-MM-dd 변환)
            val dataList = result.dates.indices.map { i ->
                val krxDate = result.dates[i]
                val isoDate = "${krxDate.substring(0, 4)}-${krxDate.substring(4, 6)}-${krxDate.substring(6)}"
                MarketOscillatorEntity(
                    id = "$market-$isoDate",
                    market = market,
                    date = isoDate,
                    indexValue = result.indexValues[i],
                    oscillator = result.oscillator[i]
                )
            }

            onProgress?.invoke("$market 데이터베이스 저장 중...", 90)
            oscillatorDao.insertAll(dataList)

            Timber.d("Initialized $market with ${dataList.size} data points")
            onProgress?.invoke("$market 완료", 100)
            Result.success(dataList.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error initializing market data")
            Result.failure(e)
        }
    }

    /**
     * 데이터 업데이트 (최근 30일)
     */
    suspend fun updateMarketData(market: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(30)
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd")

            val result = calculator.analyze(market, startDate.format(formatter), endDate.format(formatter))
            if (result == null || result.dates.isEmpty()) {
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            val dataList = result.dates.indices.map { i ->
                val krxDate = result.dates[i]
                val isoDate = "${krxDate.substring(0, 4)}-${krxDate.substring(4, 6)}-${krxDate.substring(6)}"
                MarketOscillatorEntity(
                    id = "$market-$isoDate",
                    market = market,
                    date = isoDate,
                    indexValue = result.indexValues[i],
                    oscillator = result.oscillator[i]
                )
            }

            oscillatorDao.insertAll(dataList)
            oscillatorDao.deleteOldData(market, DEFAULT_KEEP_DAYS)

            Timber.d("Updated $market with ${dataList.size} data points")
            Result.success(dataList.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error updating market data")
            Result.failure(e)
        }
    }

    // ===== 자금 동향 =====

    fun getAllDeposits(): Flow<List<MarketDeposit>> =
        depositDao.getAllDeposits()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    fun getDepositsByDateRange(startDate: String, endDate: String): Flow<List<MarketDeposit>> =
        depositDao.getByDateRange(startDate, endDate)
            .map { list -> list.map { it.toDomain() } }
            .flowOn(Dispatchers.IO)

    /**
     * 초기 자금 동향 데이터 수집
     */
    suspend fun initializeDeposits(
        numPages: Int = 5,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke("증시 자금 동향 데이터 수집 중...", 30)
            val marketData = scraper.scrapeDepositData(numPages)
                ?: return@withContext Result.failure(Exception("Naver Finance 스크래핑 실패"))

            onProgress?.invoke("데이터 처리 중...", 70)

            val deposits = marketData.dates.mapIndexed { index, date ->
                MarketDepositEntity(
                    date = date,
                    depositAmount = marketData.depositAmounts[index],
                    depositChange = marketData.depositChanges[index],
                    creditAmount = marketData.creditAmounts[index],
                    creditChange = marketData.creditChanges[index]
                )
            }

            if (deposits.isEmpty()) {
                return@withContext Result.failure(Exception("데이터가 비어있습니다"))
            }

            onProgress?.invoke("데이터베이스 저장 중...", 90)
            depositDao.deleteAll()
            depositDao.insertAll(deposits)

            onProgress?.invoke("완료", 100)
            Result.success(deposits.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error initializing market deposits")
            Result.failure(e)
        }
    }

    /**
     * 스마트 업데이트: 캐시 TTL 확인 후 필요 시 스크래핑
     */
    suspend fun getOrUpdateMarketData(limit: Int = 500): MarketDepositChartData? =
        withContext(Dispatchers.IO) {
            try {
                val existingDeposits = depositDao.getAllDeposits().first()
                val shouldUpdate = shouldUpdateMarketData(existingDeposits)

                if (!shouldUpdate && existingDeposits.isNotEmpty()) {
                    Timber.d("Using cached market deposit data (${existingDeposits.size} records)")
                    return@withContext convertToChartData(existingDeposits)
                }

                Timber.d("Fetching latest market deposit data from Naver Finance...")
                val latestData = try {
                    scraper.getLatestData()
                } catch (e: Exception) {
                    Timber.e(e, "Naver Finance scraping failed")
                    return@withContext if (existingDeposits.isNotEmpty()) convertToChartData(existingDeposits) else null
                }

                if (latestData == null) {
                    return@withContext if (existingDeposits.isNotEmpty()) convertToChartData(existingDeposits) else null
                }

                val newDeposits = latestData.dates.mapIndexed { index, date ->
                    MarketDepositEntity(
                        date = date,
                        depositAmount = latestData.depositAmounts[index],
                        depositChange = latestData.depositChanges[index],
                        creditAmount = latestData.creditAmounts[index],
                        creditChange = latestData.creditChanges[index]
                    )
                }

                depositDao.insertAll(newDeposits)

                val updatedDeposits = depositDao.getAllDeposits().first()
                convertToChartData(updatedDeposits)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error getting or updating market data")
                val existing = depositDao.getAllDeposits().first()
                if (existing.isNotEmpty()) convertToChartData(existing) else null
            }
        }

    private fun shouldUpdateMarketData(deposits: List<MarketDepositEntity>): Boolean {
        if (deposits.isEmpty()) return true

        val lastUpdate = deposits.maxOfOrNull { it.lastUpdated } ?: 0L
        val hoursSinceUpdate = (System.currentTimeMillis() - lastUpdate) / (1000 * 60 * 60)

        if (hoursSinceUpdate >= DATA_EXPIRY_HOURS) return true

        val today = LocalDate.now().toString()
        val latestDate = deposits.maxOfOrNull { it.date } ?: ""
        return latestDate != today
    }

    private fun convertToChartData(deposits: List<MarketDepositEntity>): MarketDepositChartData {
        val sorted = deposits.sortedBy { it.date }
        return MarketDepositChartData(
            dates = sorted.map { it.date },
            depositAmounts = sorted.map { it.depositAmount },
            depositChanges = sorted.map { it.depositChange },
            creditAmounts = sorted.map { it.creditAmount },
            creditChanges = sorted.map { it.creditChange }
        )
    }

    // ===== Entity ↔ Domain Mappers =====

    private fun MarketOscillatorEntity.toDomain() = MarketOscillator(
        id = id, market = market, date = date,
        indexValue = indexValue, oscillator = oscillator, lastUpdated = lastUpdated
    )

    private fun MarketDepositEntity.toDomain() = MarketDeposit(
        date = date, depositAmount = depositAmount, depositChange = depositChange,
        creditAmount = creditAmount, creditChange = creditChange, lastUpdated = lastUpdated
    )
}
