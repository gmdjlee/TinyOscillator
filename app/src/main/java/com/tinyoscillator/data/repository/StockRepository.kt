package com.tinyoscillator.data.repository

import android.util.Log
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.AnalysisCacheDao
import com.tinyoscillator.core.database.entity.AnalysisCacheEntity
import com.tinyoscillator.data.dto.*
import com.tinyoscillator.domain.model.DailyTrading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 주식 데이터 Repository.
 *
 * Kiwoom REST API를 통해 투자자 거래 데이터를 수집합니다.
 * 분석 결과를 Room DB에 캐시하여 incremental update를 지원합니다.
 */
@Singleton
class StockRepository @Inject constructor(
    private val apiClient: KiwoomApiClient,
    private val json: Json,
    private val analysisCacheDao: AnalysisCacheDao
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val lastFetchTime = mutableMapOf<String, Long>()

    /**
     * 일별 거래 데이터 수집 (incremental cache 지원).
     *
     * 1. DB에서 캐시된 데이터의 최신 날짜 조회
     * 2. 캐시가 없으면 전체 기간 API 호출, 있으면 신규 날짜만 API 호출
     * 3. 신규 데이터를 DB에 저장
     * 4. 365일 이전 데이터 정리
     * 5. DB에서 전체 기간 데이터 반환
     */
    suspend fun getDailyTradingData(
        ticker: String,
        startDate: String,
        endDate: String,
        config: KiwoomApiKeyConfig
    ): List<DailyTrading> = withContext(Dispatchers.IO) {
        if (!config.isValid()) {
            throw ApiError.NoApiKeyError()
        }

        val latestCachedDate = analysisCacheDao.getLatestDate(ticker)
        val today = LocalDate.now().format(fmt)

        if (latestCachedDate != null && latestCachedDate >= today) {
            // 캐시가 오늘까지 있음 → DB에서만 반환
            Log.d(TAG, "캐시가 최신 ($latestCachedDate >= $today) → DB에서 반환")
            return@withContext loadFromCache(ticker, startDate, endDate)
        }

        // 주말/공휴일 반복 호출 방지: 1시간 쿨다운
        if (latestCachedDate != null) {
            val lastFetch = lastFetchTime[ticker] ?: 0L
            if (System.currentTimeMillis() - lastFetch < COOLDOWN_MS) {
                Log.d(TAG, "쿨다운 중 → 캐시 반환: $ticker")
                return@withContext loadFromCache(ticker, startDate, endDate)
            }
        }

        // API에서 신규 데이터 수집
        val fetchStartDate = if (latestCachedDate != null) {
            // 캐시된 최신일 + 1일부터 조회
            val nextDay = LocalDate.parse(latestCachedDate, fmt).plusDays(1)
            nextDay.format(fmt)
        } else {
            startDate
        }

        Log.d(TAG, "━━━ incremental fetch ━━━")
        Log.d(TAG, "캐시 최신일: ${latestCachedDate ?: "없음"} → fetch: $fetchStartDate ~ $endDate")

        val newData = fetchFromApi(ticker, fetchStartDate, endDate, config)
        lastFetchTime[ticker] = System.currentTimeMillis()

        if (newData.isNotEmpty()) {
            // DB에 저장
            val entities = newData.map { daily ->
                AnalysisCacheEntity(
                    ticker = ticker,
                    date = daily.date,
                    marketCap = daily.marketCap,
                    foreignNet = daily.foreignNetBuy,
                    instNet = daily.instNetBuy
                )
            }
            analysisCacheDao.insertAll(entities)
            Log.d(TAG, "캐시 저장: ${entities.size}건 (${fetchStartDate}~${endDate})")

            // 365일 이전 데이터 정리
            val cutoffDate = LocalDate.now().minusDays(365).format(fmt)
            analysisCacheDao.deleteOlderThan(ticker, cutoffDate)
            Log.d(TAG, "365일 정리: $cutoffDate 이전 삭제")
        }

        // DB에서 전체 기간 데이터 반환
        loadFromCache(ticker, startDate, endDate)
    }

    /**
     * DB 캐시에서 데이터 로드.
     */
    private suspend fun loadFromCache(
        ticker: String,
        startDate: String,
        endDate: String
    ): List<DailyTrading> {
        val cached = analysisCacheDao.getByTickerDateRange(ticker, startDate, endDate)
        Log.d(TAG, "캐시에서 로드: ${cached.size}건 ($startDate~$endDate)")
        return cached.map { entity ->
            DailyTrading(
                date = entity.date,
                marketCap = entity.marketCap,
                foreignNetBuy = entity.foreignNet,
                instNetBuy = entity.instNet
            )
        }
    }

    /**
     * API에서 데이터 수집 (기존 로직 유지).
     */
    private suspend fun fetchFromApi(
        ticker: String,
        startDate: String,
        endDate: String,
        config: KiwoomApiKeyConfig
    ): List<DailyTrading> {
        // 병렬 데이터 수집
        val (investorTrend, stockInfo, ohlcvData) = coroutineScope {
            val trendDeferred = async { fetchInvestorTrend(ticker, endDate, config) }
            val infoDeferred = async { fetchStockInfo(ticker, config) }
            val ohlcvDeferred = async { fetchDailyOhlcv(ticker, endDate, config) }

            Triple(
                trendDeferred.await(),
                infoDeferred.await(),
                ohlcvDeferred.await()
            )
        }

        if (investorTrend.isEmpty()) {
            Log.w(TAG, "투자자 동향 데이터가 없습니다: $ticker")
            return emptyList()
        }

        // 상장주식수 (1000주 단위 → 주)
        val sharesOutstanding = stockInfo.floatingShares
        Log.d(TAG, "━━━ API 데이터 수집 결과 ━━━")
        Log.d(TAG, "종목: $ticker | 상장주식수: ${sharesOutstanding}주")

        // 종가 맵 (날짜 → 종가)
        val closePriceMap = ohlcvData.associate { it.date to it.close }
        Log.d(TAG, "투자자동향: ${investorTrend.size}건, 일봉: ${ohlcvData.size}건")

        // 투자자 동향 원본 데이터 로그 (마지막 5일)
        Log.d(TAG, "━━━ ka10059 원본 (마지막 5일, unit_tp=1000) ━━━")
        investorTrend.takeLast(5).forEach { t ->
            Log.d(TAG, "[${t.date}] foreignNet=${t.foreignNet} instNet=${t.institutionNet} marketCap=${t.marketCap}" +
                    " → foreignWon=${t.foreignNetWon} instWon=${t.instNetWon} mcapWon=${t.marketCapWon}")
        }

        // 일봉 종가 로그 (마지막 5일)
        Log.d(TAG, "━━━ ka10081 종가 (마지막 5일) ━━━")
        ohlcvData.takeLast(5).forEach { o ->
            Log.d(TAG, "[${o.date}] close=${o.close}")
        }

        // 투자자 거래 데이터를 DailyTrading으로 변환
        val result = investorTrend
            .filter { it.date >= startDate }
            .map { trend ->
                val closePrice = closePriceMap[trend.date]
                val marketCap = if (sharesOutstanding > 0 && closePrice != null && closePrice > 0) {
                    sharesOutstanding * closePrice.toLong()
                } else {
                    trend.marketCapWon
                }

                DailyTrading(
                    date = trend.date,
                    marketCap = marketCap,
                    foreignNetBuy = trend.foreignNetWon,
                    instNetBuy = trend.instNetWon
                )
            }
            .sortedBy { it.date }

        // 변환 후 DailyTrading 로그 (마지막 5일)
        Log.d(TAG, "━━━ DailyTrading 변환 결과 (마지막 5일) ━━━")
        result.takeLast(5).forEach { d ->
            Log.d(TAG, "[${d.date}] marketCap=${d.marketCap}원 (${String.format("%.2f", d.marketCap / 1_0000_0000_0000.0)}조)" +
                    " | foreignNetBuy=${d.foreignNetBuy}원 | instNetBuy=${d.instNetBuy}원")
        }
        Log.d(TAG, "━━━ 총 ${result.size}건 반환 ━━━")

        return result
    }

    /**
     * 투자자별 매매 동향 조회 (ka10059).
     */
    private suspend fun fetchInvestorTrend(
        ticker: String,
        endDate: String,
        config: KiwoomApiKeyConfig
    ): List<InvestorTrendData> {
        val body = mapOf(
            "dt" to endDate,
            "stk_cd" to ticker,
            "amt_qty_tp" to "1",     // 금액 기준
            "trde_tp" to "0",        // 순매수
            "unit_tp" to "1000"      // 백만원 단위
        )

        val result = apiClient.call(
            apiId = StockApiIds.INVESTOR_TREND,
            url = StockApiEndpoints.INVESTOR_TREND,
            body = body,
            config = config
        ) { responseJson ->
            json.decodeFromString<InvestorTrendResponse>(responseJson)
        }

        val response = result.getOrElse { error ->
            Log.e(TAG, "투자자 동향 조회 실패: ${error.message}")
            return emptyList()
        }

        return response.data?.mapNotNull { item ->
            val date = item.date ?: return@mapNotNull null
            InvestorTrendData(
                date = date,
                foreignNet = item.foreignNet ?: 0L,
                institutionNet = item.institutionNet ?: 0L,
                marketCap = item.marketCap ?: 0L
            )
        }?.sortedBy { it.date } ?: emptyList()
    }

    /**
     * 주식 기본 정보 조회 (ka10001).
     */
    private suspend fun fetchStockInfo(
        ticker: String,
        config: KiwoomApiKeyConfig
    ): StockInfoData {
        val body = mapOf("stk_cd" to ticker)

        val result = apiClient.call(
            apiId = StockApiIds.STOCK_INFO,
            url = StockApiEndpoints.STOCK_INFO,
            body = body,
            config = config
        ) { responseJson ->
            json.decodeFromString<StockInfoResponse>(responseJson)
        }

        return result.getOrElse {
            Log.w(TAG, "주식 정보 조회 실패: ${it.message}")
            return StockInfoData()
        }.let { response ->
            val floStk = response.floStk.toLongSafe()
            StockInfoData(
                name = response.stkNm ?: ticker,
                floatingShares = if (floStk > 0) floStk * 1000 else 0L
            )
        }
    }

    /**
     * 일봉 차트 조회 (ka10081).
     */
    private suspend fun fetchDailyOhlcv(
        ticker: String,
        endDate: String,
        config: KiwoomApiKeyConfig
    ): List<OhlcvEntry> {
        val body = mapOf(
            "stk_cd" to ticker,
            "base_dt" to endDate,
            "upd_stkpc_tp" to "1"
        )

        val result = apiClient.call(
            apiId = StockApiIds.DAILY_CHART,
            url = StockApiEndpoints.DAILY_CHART,
            body = body,
            config = config
        ) { responseJson ->
            json.decodeFromString<DailyOhlcvResponse>(responseJson)
        }

        return result.getOrElse {
            Log.w(TAG, "일봉 차트 조회 실패: ${it.message}")
            return emptyList()
        }.data?.mapNotNull { item ->
            val date = item.date ?: return@mapNotNull null
            val close = item.close ?: return@mapNotNull null
            OhlcvEntry(date = date, close = close)
        } ?: emptyList()
    }

    private fun String?.toLongSafe(): Long =
        this?.removePrefix("+")?.replace(",", "")?.trim()?.toLongOrNull() ?: 0L

    companion object {
        private const val TAG = "StockRepository"
        private const val COOLDOWN_MS = 60 * 60 * 1000L  // 1시간
    }
}

data class StockSearchResult(
    val ticker: String,
    val name: String,
    val market: String
)

private data class InvestorTrendData(
    val date: String,
    val foreignNet: Long,       // 천원 단위 (unit_tp="1000")
    val institutionNet: Long,   // 천원 단위 (unit_tp="1000")
    val marketCap: Long         // 백만원 단위
) {
    /** 외국인 순매수 (원): unit_tp="1000" → 백만원 단위 응답 */
    val foreignNetWon: Long get() = foreignNet * 1_000_000

    /** 기관 순매수 (원): unit_tp="1000" → 백만원 단위 응답 */
    val instNetWon: Long get() = institutionNet * 1_000_000

    /** 시가총액 (원) */
    val marketCapWon: Long get() = marketCap * 1_000_000
}

private data class StockInfoData(
    val name: String = "",
    val floatingShares: Long = 0L  // 상장주식수 (주)
)

private data class OhlcvEntry(
    val date: String,
    val close: Int
)
