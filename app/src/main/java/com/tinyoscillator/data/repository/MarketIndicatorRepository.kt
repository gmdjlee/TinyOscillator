package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.MarketDepositDao
import com.tinyoscillator.core.database.dao.MarketOscillatorDao
import com.tinyoscillator.core.database.entity.MarketDepositEntity
import com.tinyoscillator.core.database.entity.MarketOscillatorEntity
import com.tinyoscillator.core.api.KrxApiClient
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
import com.tinyoscillator.core.util.DateFormats
import java.time.LocalDate

private const val DEPOSIT_KEEP_DAYS = 365L

/**
 * 시장지표 통합 Repository
 * - 과매수/과매도 (MarketOscillator)
 * - 자금 동향 (MarketDeposit)
 */
class MarketIndicatorRepository(
    private val oscillatorDao: MarketOscillatorDao,
    private val depositDao: MarketDepositDao,
    private val calculator: MarketOscillatorCalculator,
    private val scraper: NaverFinanceScraper,
    private val krxApiClient: KrxApiClient
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

    suspend fun getRecentData(market: String, limit: Int): List<MarketOscillator> =
        withContext(Dispatchers.IO) {
            oscillatorDao.getRecentData(market, limit).map { it.toDomain() }
        }

    suspend fun getOscillatorLastUpdateTime(): Long? =
        withContext(Dispatchers.IO) {
            oscillatorDao.getLastUpdateTime()
        }

    suspend fun getDepositLastUpdateTime(): Long? =
        withContext(Dispatchers.IO) {
            depositDao.getLastUpdateTime()
        }

    suspend fun getRecentDeposits(limit: Int): List<MarketDeposit> =
        withContext(Dispatchers.IO) {
            depositDao.getRecentDeposits(limit).map { it.toDomain() }
        }

    /**
     * 초기 데이터 수집
     */
    suspend fun initializeMarketData(
        market: String,
        days: Int,
        krxId: String,
        krxPassword: String,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Initializing $market data for $days days")
            onProgress?.invoke("$market 데이터 수집 준비 중...", 0)

            if (!krxApiClient.login(krxId, krxPassword)) {
                return@withContext Result.failure(Exception("KRX 로그인 실패"))
            }

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong())
            val formatter = DateFormats.yyyyMMdd

            onProgress?.invoke("$market 시장 지수 데이터 수집 중...", 30)
            val result = calculator.analyze(market, startDate.format(formatter), endDate.format(formatter))

            if (result == null) {
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            onProgress?.invoke("$market 데이터 처리 중...", 70)

            if (result.dates.isEmpty()) {
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            val dataList = toEntities(market, result)

            onProgress?.invoke("$market 데이터베이스 저장 중...", 90)
            val existingCount = oscillatorDao.getDataCount(market)
            oscillatorDao.insertAll(dataList)

            if (existingCount > 0) {
                Timber.i("$market 초기화 (기존 ${existingCount}건 위에 ${dataList.size}건 upsert)")
            } else {
                Timber.d("$market 초기 수집 완료: ${dataList.size}건")
            }
            onProgress?.invoke("$market 완료", 100)
            Result.success(dataList.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error initializing market data")
            Result.failure(e)
        } finally {
            krxApiClient.close()
        }
    }

    /**
     * 데이터 업데이트 (기본 최근 30일, days 파라미터로 조절 가능)
     */
    suspend fun updateMarketData(
        market: String,
        krxId: String,
        krxPassword: String,
        days: Int = 30
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!krxApiClient.login(krxId, krxPassword)) {
                return@withContext Result.failure(Exception("KRX 로그인 실패"))
            }

            val endDate = LocalDate.now()
            val startDate = endDate.minusDays(days.toLong())
            val formatter = DateFormats.yyyyMMdd

            val result = calculator.analyze(market, startDate.format(formatter), endDate.format(formatter))
            if (result == null || result.dates.isEmpty()) {
                return@withContext Result.failure(Exception("데이터를 가져오지 못했습니다"))
            }

            val dataList = toEntities(market, result)
            val existingCount = oscillatorDao.getDataCount(market)

            oscillatorDao.insertAndCleanup(dataList, market, DEFAULT_KEEP_DAYS)

            val afterCount = oscillatorDao.getDataCount(market)
            Timber.i("$market 업데이트: ${dataList.size}건 upsert (기존 ${existingCount} → ${afterCount}건, ${DEFAULT_KEEP_DAYS}일 초과 정리)")
            Result.success(dataList.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error updating market data")
            Result.failure(e)
        } finally {
            krxApiClient.close()
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

            val size = marketData.dates.size
            if (size == 0 ||
                marketData.depositAmounts.size != size ||
                marketData.depositChanges.size != size ||
                marketData.creditAmounts.size != size ||
                marketData.creditChanges.size != size
            ) {
                return@withContext Result.failure(Exception("데이터가 비어있거나 리스트 크기가 일치하지 않습니다"))
            }

            val deposits = marketData.dates.mapIndexed { index, date ->
                MarketDepositEntity(
                    date = date,
                    depositAmount = marketData.depositAmounts[index],
                    depositChange = marketData.depositChanges[index],
                    creditAmount = marketData.creditAmounts[index],
                    creditChange = marketData.creditChanges[index]
                )
            }

            onProgress?.invoke("데이터베이스 저장 중...", 90)
            val existingCount = depositDao.getCount()
            val cutoffDate = LocalDate.now().minusDays(DEPOSIT_KEEP_DAYS).toString()
            depositDao.insertAndCleanup(deposits, cutoffDate)
            val afterCount = depositDao.getCount()

            Timber.i("자금동향 초기화: ${deposits.size}건 upsert (기존 ${existingCount} → ${afterCount}건, $cutoffDate 이전 정리)")

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
    suspend fun getOrUpdateMarketData(
        daysBack: Int = 365,
        onProgress: ((String, Int) -> Unit)? = null
    ): MarketDepositChartData? =
        withContext(Dispatchers.IO) {
            try {
                onProgress?.invoke("캐시 확인 중...", 10)
                val existingDeposits = depositDao.getAllDeposits().first()
                val shouldUpdate = shouldUpdateMarketData(existingDeposits)

                if (!shouldUpdate && existingDeposits.isNotEmpty()) {
                    Timber.d("Using cached market deposit data (${existingDeposits.size} records)")
                    onProgress?.invoke("캐시 데이터 사용", 100)
                    return@withContext convertToChartData(existingDeposits)
                }

                val numPages = (daysBack / 20).coerceIn(1, 50)
                onProgress?.invoke("자금 동향 데이터 수집 중 (${numPages}페이지)...", 30)
                Timber.d("Fetching market deposit data: daysBack=$daysBack, numPages=$numPages")
                val latestData = try {
                    scraper.scrapeDepositData(numPages)
                } catch (e: Exception) {
                    Timber.e(e, "Naver Finance scraping failed")
                    if (existingDeposits.isNotEmpty()) {
                        Timber.w("스크래핑 실패 → 기존 캐시 ${existingDeposits.size}건으로 fallback")
                    }
                    return@withContext if (existingDeposits.isNotEmpty()) convertToChartData(existingDeposits) else null
                }

                if (latestData == null) {
                    Timber.w("스크래핑 null 반환 → 기존 캐시 ${existingDeposits.size}건으로 fallback")
                    return@withContext if (existingDeposits.isNotEmpty()) convertToChartData(existingDeposits) else null
                }

                onProgress?.invoke("데이터 처리 중...", 70)
                val size = latestData.dates.size
                if (size == 0 ||
                    latestData.depositAmounts.size != size ||
                    latestData.depositChanges.size != size ||
                    latestData.creditAmounts.size != size ||
                    latestData.creditChanges.size != size
                ) {
                    Timber.w("스크래핑 데이터 리스트 크기 불일치 → 기존 캐시로 fallback")
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

                onProgress?.invoke("데이터베이스 저장 중...", 90)
                val beforeCount = depositDao.getCount()
                val cutoffDate = LocalDate.now().minusDays(DEPOSIT_KEEP_DAYS).toString()
                depositDao.insertAndCleanup(newDeposits, cutoffDate)
                val afterCount = depositDao.getCount()

                Timber.i("자금동향 업데이트: ${newDeposits.size}건 upsert (기존 ${beforeCount} → ${afterCount}건, $cutoffDate 이전 정리)")

                onProgress?.invoke("완료", 100)
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

        return hoursSinceUpdate >= DATA_EXPIRY_HOURS
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

    private fun toEntities(market: String, result: MarketOscillatorCalculator.OscillatorResult): List<MarketOscillatorEntity> =
        result.dates.indices.map { i ->
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
