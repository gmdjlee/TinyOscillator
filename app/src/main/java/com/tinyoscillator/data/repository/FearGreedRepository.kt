package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KrxApiClient
import com.tinyoscillator.core.database.dao.FearGreedDao
import com.tinyoscillator.core.database.entity.FearGreedEntity
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.FearGreedChartData
import com.tinyoscillator.domain.model.FearGreedRow
import com.tinyoscillator.domain.model.MarketDemarkChartData
import com.tinyoscillator.domain.model.MarketDemarkRow
import com.tinyoscillator.domain.usecase.CalcMarketDemarkUseCase
import com.tinyoscillator.domain.usecase.FearGreedCalculator
import com.tinyoscillator.domain.usecase.FearGreedCalculator.FearGreedDayData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Fear & Greed 지수 Repository.
 *
 * 7개 KRX API에서 데이터를 수집하여 FearGreedCalculator로 계산 후 Room DB에 저장한다.
 * - Call/Put 옵션 거래량 (5일 이동평균)
 * - 5년/10년 국채 금리
 * - VKOSPI (변동성 지수)
 * - KOSPI/KOSDAQ 지수
 */
class FearGreedRepository(
    private val fearGreedDao: FearGreedDao,
    private val krxApiClient: KrxApiClient
) {
    companion object {
        private const val MIN_ROWS = 15
        private const val KRX_BATCH_DELAY_MS = 2000L
        private const val UPDATE_DAYS = 150
    }

    private val krxDateFmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val isoDateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val demarkUseCase = CalcMarketDemarkUseCase()

    // ===== 읽기 API =====

    fun getChartData(market: String, startDate: String, endDate: String): Flow<FearGreedChartData> =
        fearGreedDao.getByMarketAndDateRange(market, startDate, endDate)
            .map { list ->
                FearGreedChartData(
                    market = market,
                    rows = list.map { e ->
                        FearGreedRow(e.date, e.indexValue, e.fearGreedValue, e.oscillator)
                    }
                )
            }
            .flowOn(Dispatchers.IO)

    fun getAllByMarket(market: String): Flow<List<FearGreedEntity>> =
        fearGreedDao.getAllByMarket(market).flowOn(Dispatchers.IO)

    suspend fun getCountByMarket(market: String): Int =
        withContext(Dispatchers.IO) { fearGreedDao.getCountByMarket(market) }

    suspend fun getLatestDate(market: String): String? =
        withContext(Dispatchers.IO) { fearGreedDao.getLatestDate(market) }

    suspend fun getRecentData(market: String, limit: Int): List<FearGreedEntity> =
        withContext(Dispatchers.IO) { fearGreedDao.getRecentData(market, limit) }

    // ===== 초기 수집 =====

    /**
     * Fear & Greed 데이터 초기 수집.
     * MA warm-up을 보정하기 위해 요청일수의 3배(최대 730일)를 수집한다.
     */
    suspend fun initializeFearGreed(
        days: Int = 365,
        krxId: String,
        krxPassword: String,
        onProgress: ((String, Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val collectDays = minOf(days * 3, 730)
            val endDate = LocalDate.now().format(krxDateFmt)
            val startDate = LocalDate.now().minusDays(collectDays.toLong()).format(krxDateFmt)

            onProgress?.invoke("KRX 로그인 중...", 5)

            val loggedIn = krxApiClient.login(krxId, krxPassword)
            if (!loggedIn) return@withContext Result.failure(Exception("KRX 로그인 실패"))

            onProgress?.invoke("Fear & Greed 데이터 수집 중...", 10)

            val entities = fetchAndCalculate(startDate, endDate) { msg, pct ->
                onProgress?.invoke(msg, 10 + (pct * 0.8).toInt())
            }

            if (entities.isEmpty()) {
                return@withContext Result.failure(Exception("수집된 데이터가 없습니다"))
            }

            onProgress?.invoke("데이터베이스 저장 중...", 92)

            withContext(NonCancellable) {
                fearGreedDao.deleteAll()
                fearGreedDao.insertAll(entities)
            }

            onProgress?.invoke("완료", 100)
            Timber.i("Fear & Greed 초기 수집 완료: ${entities.size}건")
            Result.success(entities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Fear & Greed 초기 수집 실패")
            Result.failure(e)
        }
    }

    // ===== 업데이트 (롤링) =====

    suspend fun updateFearGreed(
        krxId: String,
        krxPassword: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val endDate = LocalDate.now().format(krxDateFmt)
            val startDate = LocalDate.now().minusDays(UPDATE_DAYS.toLong()).format(krxDateFmt)

            val loggedIn = krxApiClient.login(krxId, krxPassword)
            if (!loggedIn) return@withContext Result.failure(Exception("KRX 로그인 실패"))

            val entities = fetchAndCalculate(startDate, endDate, null)

            if (entities.isEmpty()) {
                return@withContext Result.failure(Exception("수집된 데이터가 없습니다"))
            }

            withContext(NonCancellable) {
                fearGreedDao.insertAll(entities)  // REPLACE strategy
            }

            Timber.i("Fear & Greed 업데이트 완료: ${entities.size}건")
            Result.success(entities.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Fear & Greed 업데이트 실패")
            Result.failure(e)
        }
    }

    // ===== 시장 DeMark =====

    /**
     * 시장 지수(KOSPI/KOSDAQ)의 DeMark TD Sequential 계산.
     */
    suspend fun getMarketDemarkData(
        market: String,
        days: Int,
        periodType: DemarkPeriodType,
        krxId: String,
        krxPassword: String
    ): Result<MarketDemarkChartData> = withContext(Dispatchers.IO) {
        try {
            val loggedIn = krxApiClient.login(krxId, krxPassword)
            if (!loggedIn) return@withContext Result.failure(Exception("KRX 로그인 실패"))

            val krxIndex = krxApiClient.getKrxIndex()
                ?: return@withContext Result.failure(Exception("KRX 인덱스 클라이언트 없음"))

            val endDate = LocalDate.now().format(krxDateFmt)
            val startDate = LocalDate.now().minusDays(days.toLong()).format(krxDateFmt)

            val indexData = when (market) {
                "KOSPI" -> krxIndex.getKospi(startDate, endDate)
                "KOSDAQ" -> krxIndex.getKosdaq(startDate, endDate)
                else -> return@withContext Result.failure(Exception("지원하지 않는 시장: $market"))
            }

            if (indexData.size < 5) {
                return@withContext Result.failure(Exception("데이터 부족 (${indexData.size}건)"))
            }

            val inputData = indexData.map { ohlcv ->
                CalcMarketDemarkUseCase.IndexDay(
                    date = ohlcv.date,
                    close = ohlcv.close
                )
            }

            val rows = demarkUseCase.execute(inputData, periodType)

            Result.success(MarketDemarkChartData(market, rows, periodType))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "시장 DeMark 계산 실패")
            Result.failure(e)
        }
    }

    // ===== 내부 수집/계산 파이프라인 =====

    private suspend fun fetchAndCalculate(
        startDate: String,
        endDate: String,
        onProgress: ((String, Int) -> Unit)?
    ): List<FearGreedEntity> = coroutineScope {
        onProgress?.invoke("옵션/채권 데이터 수집 중...", 10)

        val krxIndex = krxApiClient.getKrxIndex()
            ?: throw Exception("KRX 인덱스 클라이언트가 초기화되지 않았습니다")

        // Batch 1: 옵션 + 채권 (4개 병렬)
        val callVolDeferred = async { runCatching { krxIndex.getCallOptionVolume(startDate, endDate) } }
        val putVolDeferred = async { runCatching { krxIndex.getPutOptionVolume(startDate, endDate) } }
        val bond5yDeferred = async { runCatching { krxIndex.getBond5y(startDate, endDate) } }
        val bond10yDeferred = async { runCatching { krxIndex.getBond10y(startDate, endDate) } }

        val callVol = callVolDeferred.await().getOrElse { emptyList() }
        val putVol = putVolDeferred.await().getOrElse { emptyList() }
        val bond5y = bond5yDeferred.await().getOrElse { emptyList() }
        val bond10y = bond10yDeferred.await().getOrElse { emptyList() }

        onProgress?.invoke("VKOSPI/지수 데이터 수집 중...", 40)

        // KRX rate limit 대기
        delay(KRX_BATCH_DELAY_MS)

        // Batch 2: 변동성 + 지수 (3개 병렬)
        val vkospiDeferred = async { runCatching { krxIndex.getVkospi(startDate, endDate) } }
        val kospiDeferred = async { runCatching { krxIndex.getKospi(startDate, endDate) } }
        val kosdaqDeferred = async { runCatching { krxIndex.getKosdaq(startDate, endDate) } }

        val vkospi = vkospiDeferred.await().getOrElse { emptyList() }
        val kospi = kospiDeferred.await().getOrElse { emptyList() }
        val kosdaq = kosdaqDeferred.await().getOrElse { emptyList() }

        onProgress?.invoke("데이터 병합 및 계산 중...", 70)

        // 5일 이동평균 적용
        val sortedCall = callVol.sortedBy { it.date }
        val sortedPut = putVol.sortedBy { it.date }
        val callMa = FearGreedCalculator.rollingMean5(sortedCall.map { it.totalVolume })
        val putMa = FearGreedCalculator.rollingMean5(sortedPut.map { it.totalVolume })

        // 날짜별 lookup map 생성 (yyyyMMdd → 값)
        val callMap = mutableMapOf<String, Double>()
        sortedCall.forEachIndexed { i, ov -> if (callMa[i].isFinite()) callMap[ov.date] = callMa[i] }
        val putMap = mutableMapOf<String, Double>()
        sortedPut.forEachIndexed { i, ov -> if (putMa[i].isFinite()) putMap[ov.date] = putMa[i] }

        val bond5yMap = bond5y.associate { it.date to it.close }
        val bond10yMap = bond10y.associate { it.date to it.close }
        val vkospiMap = vkospi.associate { it.date to it.close }
        val kospiMap = kospi.associate { it.date to it.close }
        val kosdaqMap = kosdaq.associate { it.date to it.close }

        // 모든 날짜 합집합 (공통 필수 데이터가 있는 행만)
        val allDates = (callMap.keys + putMap.keys + bond5yMap.keys + bond10yMap.keys + vkospiMap.keys)
            .toSortedSet()

        data class MergedRow(
            val date: String,
            val call: Double, val put: Double,
            val bond5y: Double, val bond10y: Double,
            val vix: Double,
            val kospi: Double?, val kosdaq: Double?
        )

        val mergedRows = allDates.mapNotNull { date ->
            val c = callMap[date] ?: return@mapNotNull null
            val p = putMap[date] ?: return@mapNotNull null
            val b5 = bond5yMap[date] ?: return@mapNotNull null
            val b10 = bond10yMap[date] ?: return@mapNotNull null
            val v = vkospiMap[date] ?: return@mapNotNull null
            MergedRow(date, c, p, b5, b10, v, kospiMap[date], kosdaqMap[date])
        }

        if (mergedRows.size < MIN_ROWS) {
            Timber.w("병합 데이터 부족: ${mergedRows.size}건 (최소: $MIN_ROWS)")
            return@coroutineScope emptyList()
        }

        onProgress?.invoke("Fear & Greed 지수 계산 중...", 80)

        // 시장별 계산
        val entities = mutableListOf<FearGreedEntity>()

        fun calcForMarket(market: String, indexValues: List<Double?>) {
            val validRows = mergedRows.indices.filter { indexValues[it] != null }
            if (validRows.size < MIN_ROWS) return

            val dayDataList = validRows.map { i ->
                val row = mergedRows[i]
                val isoDate = convertToIsoDate(row.date)
                FearGreedDayData(
                    date = isoDate,
                    indexValue = indexValues[i]!!,
                    call = row.call,
                    put = row.put,
                    vix = row.vix,
                    bond5y = row.bond5y,
                    bond10y = row.bond10y
                )
            }

            val results = FearGreedCalculator.calcFearGreed(dayDataList)

            results.filter { it.fearGreedValue.isFinite() && it.oscillator.isFinite() }
                .forEach { r ->
                    entities.add(
                        FearGreedEntity(
                            id = "$market-${r.date}",
                            market = market,
                            date = r.date,
                            indexValue = r.indexValue,
                            fearGreedValue = r.fearGreedValue,
                            oscillator = r.oscillator,
                            rsi = r.rsi,
                            momentum = r.momentum,
                            putCallRatio = r.putCallRatio,
                            volatility = r.volatility,
                            spread = r.spread
                        )
                    )
                }
        }

        calcForMarket("KOSPI", mergedRows.map { it.kospi })
        calcForMarket("KOSDAQ", mergedRows.map { it.kosdaq })

        onProgress?.invoke("계산 완료: ${entities.size}건", 90)
        Timber.d("Fear & Greed 계산 완료: ${entities.size}건 (KOSPI+KOSDAQ)")

        entities
    }

    private fun convertToIsoDate(yyyyMMdd: String): String {
        return try {
            val date = LocalDate.parse(yyyyMMdd, krxDateFmt)
            date.format(isoDateFmt)
        } catch (e: Exception) {
            yyyyMMdd  // fallback
        }
    }
}
