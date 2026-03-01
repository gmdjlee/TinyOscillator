package com.tinyoscillator.data.repository

import android.util.Log
import com.tinyoscillator.core.api.ApiError
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.data.dto.*
import com.tinyoscillator.domain.model.DailyTrading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 주식 데이터 Repository.
 *
 * Kiwoom REST API를 통해 투자자 거래 데이터를 수집합니다.
 * - 종목 검색: ka10099
 * - 투자자별 매매 + 시가총액: ka10059
 * - 주식 기본 정보 (상장주식수): ka10001
 * - 일봉 차트 (종가): ka10081
 */
class StockRepository(
    private val apiClient: KiwoomApiClient = KiwoomApiClient(),
    private val json: Json = KiwoomApiClient.createDefaultJson()
) {
    private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * 종목 검색.
     */
    suspend fun searchStock(
        query: String,
        config: KiwoomApiKeyConfig
    ): List<StockSearchResult> = withContext(Dispatchers.IO) {
        if (!config.isValid()) {
            throw ApiError.NoApiKeyError()
        }

        val body = mapOf("mrkt_tp" to "0")

        val result = apiClient.call(
            apiId = StockApiIds.STOCK_LIST,
            url = StockApiEndpoints.STOCK_LIST,
            body = body,
            config = config
        ) { responseJson ->
            json.decodeFromString<StockListResponse>(responseJson)
        }

        val response = result.getOrThrow()
        val items = response.stkList ?: emptyList()

        items.filter { item ->
            val name = item.stkNm ?: ""
            val code = item.stkCd ?: ""
            query.uppercase() in name.uppercase() ||
                name.uppercase() in query.uppercase() ||
                code == query
        }.map { item ->
            StockSearchResult(
                ticker = item.stkCd ?: "",
                name = item.stkNm ?: "",
                market = item.mrktNm ?: ""
            )
        }
    }

    /**
     * 일별 거래 데이터 수집.
     *
     * Kiwoom ka10059 (투자자별 매매)를 사용하여 한 번의 API 호출로
     * 외국인/기관 순매수와 시가총액을 동시에 조회합니다.
     *
     * 추가로 ka10001 (주식 기본정보)에서 상장주식수를,
     * ka10081 (일봉)에서 종가를 조회하여 정확한 시가총액을 계산합니다.
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
            return@withContext emptyList()
        }

        // 상장주식수 (1000주 단위 → 주)
        val sharesOutstanding = stockInfo.floatingShares

        // 종가 맵 (날짜 → 종가)
        val closePriceMap = ohlcvData.associate { it.date to it.close }

        // 투자자 거래 데이터를 DailyTrading으로 변환
        investorTrend
            .filter { it.date >= startDate }
            .map { trend ->
                // 시가총액 계산 우선순위:
                // 1. 종가 × 상장주식수 (가장 정확)
                // 2. API의 mrkt_tot_amt (백만원 단위)
                val closePrice = closePriceMap[trend.date]
                val marketCap = if (sharesOutstanding > 0 && closePrice != null && closePrice > 0) {
                    sharesOutstanding * closePrice.toLong()
                } else {
                    // ka10059의 mrkt_tot_amt는 백만원 단위 → 원으로 변환
                    trend.marketCapWon
                }

                DailyTrading(
                    date = trend.date,
                    marketCap = marketCap,
                    // ka10059의 투자자 순매수 (unit_tp="1000" → 1000원 단위 → 원으로 변환)
                    foreignNetBuy = trend.foreignNetWon,
                    instNetBuy = trend.instNetWon
                )
            }
            .sortedBy { it.date }
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
            "unit_tp" to "1000"      // 1000원 단위
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
     * 상장주식수 확보용.
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
                floatingShares = if (floStk > 0) floStk * 1000 else 0L  // 1000주 단위 → 주
            )
        }
    }

    /**
     * 일봉 차트 조회 (ka10081).
     * 종가 확보용.
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
    }
}

data class StockSearchResult(
    val ticker: String,
    val name: String,
    val market: String
)

private data class InvestorTrendData(
    val date: String,
    val foreignNet: Long,       // 1000원 단위
    val institutionNet: Long,   // 1000원 단위
    val marketCap: Long         // 백만원 단위
) {
    /** 외국인 순매수 (원) */
    val foreignNetWon: Long get() = foreignNet * 1000

    /** 기관 순매수 (원) */
    val instNetWon: Long get() = institutionNet * 1000

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
