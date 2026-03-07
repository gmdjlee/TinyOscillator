/**
 * KIS API - 한국 야간 선물 지수 차트 조회
 *
 * 사용 가능한 API:
 * 1. [분봉 차트]  inquire_time_fuopchartprice  → TR_ID: FHKIF03020200
 * 2. [일봉 차트]  inquire_daily_fuopchartprice → TR_ID: FHKIF03030100
 * 3. [실시간체결] krx_ngt_futures_ccnl         → TR_ID: H0MFCNT0 (WebSocket)
 *
 * ※ 야간 선물 전용 차트 API는 없으나, 분봉/일봉 API에서 야간 시간대
 *    (18:00 ~ 익일 05:00)를 FID_INPUT_HOUR_1 파라미터로 지정하면
 *    야간 선물 데이터 조회 가능합니다.
 */

package com.example.stockapp.kis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ──────────────────────────────────────────────────────────────
// 데이터 모델
// ──────────────────────────────────────────────────────────────

/** 선물옵션 분봉 캔들 데이터 */
data class FuturesMinuteCandle(
    val date: String,           // 주식 영업 일자 (stck_bsop_date)
    val time: String,           // 주식 체결 시간 (stck_cntg_hour)
    val openPrice: Double,      // 선물 시가 (futs_oprc)
    val highPrice: Double,      // 선물 최고가 (futs_hgpr)
    val lowPrice: Double,       // 선물 최저가 (futs_lwpr)
    val closePrice: Double,     // 선물 현재가/종가 (futs_prpr)
    val volume: Long,           // 체결 거래량 (cntg_vol)
    val tradingValue: Long      // 누적 거래 대금 (acml_tr_pbmn)
)

/** KRX야간선물 실시간 체결 데이터 */
data class NightFuturesRealtimeData(
    val shortCode: String,      // 선물 단축 종목코드 (futs_shrn_iscd)
    val time: String,           // 영업 시간 (bsop_hour)
    val currentPrice: Double,   // 선물 현재가 (futs_prpr)
    val changeSign: String,     // 전일 대비 부호 (prdy_vrss_sign)
    val change: Double,         // 선물 전일 대비 (futs_prdy_vrss)
    val changeRate: Double,     // 선물 전일 대비율 (futs_prdy_ctrt)
    val openPrice: Double,      // 선물 시가 (futs_oprc)
    val highPrice: Double,      // 선물 최고가 (futs_hgpr)
    val lowPrice: Double,       // 선물 최저가 (futs_lwpr)
    val volume: Long,           // 누적 거래량 (acml_vol)
    val askPrice: Double,       // 선물 매도호가 (futs_askp1)
    val bidPrice: Double        // 선물 매수호가 (futs_bidp1)
)

// ──────────────────────────────────────────────────────────────
// API 클라이언트
// ──────────────────────────────────────────────────────────────

class KisNightFuturesClient(
    private val appKey: String,
    private val appSecret: String,
    private val accessToken: String,
    private val isReal: Boolean = true   // true: 실전, false: 모의투자
) {
    private val baseUrl = if (isReal) "https://openapi.koreainvestment.com:9443"
                          else        "https://openapivts.koreainvestment.com:29443"

    /**
     * [분봉 차트] 선물옵션 분봉조회 (야간 시간대 포함)
     * API: FHKIF03020200
     *
     * 야간 선물 차트를 보려면:
     *   - fid_input_date_1: 야간 시작 날짜 (ex. "20250305")
     *   - fid_input_hour_1: 야간 시작 시간 (ex. "180000" = 18:00:00)
     *
     * @param marketDivCode  시장 구분: "F"=지수선물, "O"=지수옵션
     * @param inputIscd      종목코드 (ex. "101W9000" = 코스피200 선물 야간)
     * @param hourClsCode    시간 구분: "30"=30초봉, "60"=1분봉
     * @param inclPastData   과거 데이터 포함: "Y"=과거, "N"=당일
     * @param inputDate      조회 날짜 (YYYYMMDD)
     * @param inputHour      조회 시작 시간 (HHMMSS)
     * @return 분봉 캔들 리스트 (최대 102건, 이후 페이지네이션 필요)
     */
    suspend fun inquireTimeFuopChartPrice(
        marketDivCode: String = "F",
        inputIscd: String = "101W9000",     // 코스피200 야간선물 종목코드
        hourClsCode: String = "60",          // 1분봉
        inclPastData: String = "Y",
        inputDate: String,
        inputHour: String = "180000"         // 야간 시작 시간 18:00
    ): Result<List<FuturesMinuteCandle>> = withContext(Dispatchers.IO) {
        try {
            val urlStr = "$baseUrl/uapi/domestic-futureoption/v1/quotations/inquire-time-fuopchartprice" +
                "?FID_COND_MRKT_DIV_CODE=$marketDivCode" +
                "&FID_INPUT_ISCD=$inputIscd" +
                "&FID_HOUR_CLS_CODE=$hourClsCode" +
                "&FID_PW_DATA_INCU_YN=$inclPastData" +
                "&FID_FAKE_TICK_INCU_YN=N" +
                "&FID_INPUT_DATE_1=$inputDate" +
                "&FID_INPUT_HOUR_1=$inputHour"

            val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("appkey", appKey)
                setRequestProperty("appsecret", appSecret)
                setRequestProperty("tr_id", "FHKIF03020200")
                setRequestProperty("custtype", "P")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(
                    Exception("HTTP Error: $responseCode")
                )
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)

            // rt_cd: "0" = 정상
            val rtCd = json.optString("rt_cd")
            if (rtCd != "0") {
                val msg = json.optString("msg1", "Unknown error")
                return@withContext Result.failure(Exception("API Error: $msg"))
            }

            val output2 = json.optJSONArray("output2") ?: return@withContext Result.success(emptyList())

            val candles = mutableListOf<FuturesMinuteCandle>()
            for (i in 0 until output2.length()) {
                val item = output2.getJSONObject(i)
                candles.add(
                    FuturesMinuteCandle(
                        date         = item.optString("stck_bsop_date"),
                        time         = item.optString("stck_cntg_hour"),
                        openPrice    = item.optString("futs_oprc").toDoubleOrNull() ?: 0.0,
                        highPrice    = item.optString("futs_hgpr").toDoubleOrNull() ?: 0.0,
                        lowPrice     = item.optString("futs_lwpr").toDoubleOrNull() ?: 0.0,
                        closePrice   = item.optString("futs_prpr").toDoubleOrNull() ?: 0.0,
                        volume       = item.optString("cntg_vol").toLongOrNull() ?: 0L,
                        tradingValue = item.optString("acml_tr_pbmn").toLongOrNull() ?: 0L
                    )
                )
            }
            Result.success(candles)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * [일봉 차트] 선물옵션 기간별 시세 조회 (일/주/월/년)
     * API: FHKIF03030100
     *
     * @param marketDivCode  시장 구분: "F"=지수선물, "O"=지수옵션
     * @param inputIscd      종목코드 (ex. "101W9000")
     * @param periodDivCode  기간 구분: "D"=일, "W"=주, "M"=월, "Y"=년
     * @param startDate      조회 시작일 (YYYYMMDD)
     * @param endDate        조회 종료일 (YYYYMMDD)
     */
    suspend fun inquireDailyFuopChartPrice(
        marketDivCode: String = "F",
        inputIscd: String = "101W9000",
        periodDivCode: String = "D",
        startDate: String,
        endDate: String
    ): Result<List<FuturesMinuteCandle>> = withContext(Dispatchers.IO) {
        try {
            val urlStr = "$baseUrl/uapi/domestic-futureoption/v1/quotations/inquire-daily-fuopchartprice" +
                "?FID_COND_MRKT_DIV_CODE=$marketDivCode" +
                "&FID_INPUT_ISCD=$inputIscd" +
                "&FID_PERIOD_DIV_CODE=$periodDivCode" +
                "&FID_INPUT_DATE_1=$startDate" +
                "&FID_INPUT_DATE_2=$endDate"

            val connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("appkey", appKey)
                setRequestProperty("appsecret", appSecret)
                setRequestProperty("tr_id", "FHKIF03030100")
                setRequestProperty("custtype", "P")
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(responseBody)

            if (json.optString("rt_cd") != "0") {
                return@withContext Result.failure(
                    Exception("API Error: ${json.optString("msg1")}")
                )
            }

            val output2 = json.optJSONArray("output2") ?: return@withContext Result.success(emptyList())
            val candles = mutableListOf<FuturesMinuteCandle>()
            for (i in 0 until output2.length()) {
                val item = output2.getJSONObject(i)
                candles.add(
                    FuturesMinuteCandle(
                        date         = item.optString("stck_bsop_date"),
                        time         = "",
                        openPrice    = item.optString("futs_oprc").toDoubleOrNull() ?: 0.0,
                        highPrice    = item.optString("futs_hgpr").toDoubleOrNull() ?: 0.0,
                        lowPrice     = item.optString("futs_lwpr").toDoubleOrNull() ?: 0.0,
                        closePrice   = item.optString("futs_prpr").toDoubleOrNull() ?: 0.0,
                        volume       = item.optString("acml_vol").toLongOrNull() ?: 0L,
                        tradingValue = item.optString("acml_tr_pbmn").toLongOrNull() ?: 0L
                    )
                )
            }
            Result.success(candles)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ──────────────────────────────────────────────────────────────
// 야간 선물 주요 종목코드 상수
// ──────────────────────────────────────────────────────────────

object NightFuturesCode {
    /**
     * 야간 선물 종목코드 형식: 101W{월물}
     * ex) 101W9000 → 2025년 9월물 코스피200 야간선물
     *
     * 실제 종목코드는 KIS API 종목 마스터 파일에서 확인:
     * https://github.com/koreainvestment/open-trading-api/tree/main/stocks_info
     */
    const val KOSPI200_NIGHT_NEAR = "101W9000"   // 코스피200 야간선물 (근월물 예시)

    /** 야간 거래시간: 18:00 ~ 익일 05:00 (KST) */
    const val NIGHT_SESSION_START = "180000"
    const val NIGHT_SESSION_END   = "050000"
}

// ──────────────────────────────────────────────────────────────
// 사용 예시 (ViewModel 등에서 호출)
// ──────────────────────────────────────────────────────────────

/*
class NightFuturesViewModel : ViewModel() {

    private val client = KisNightFuturesClient(
        appKey      = "YOUR_APP_KEY",
        appSecret   = "YOUR_APP_SECRET",
        accessToken = "YOUR_ACCESS_TOKEN"
    )

    // 오늘 야간 선물 1분봉 차트 조회
    fun loadNightFuturesMinuteChart() {
        viewModelScope.launch {
            val today = SimpleDateFormat("yyyyMMdd", Locale.KOREA).format(Date())
            val result = client.inquireTimeFuopChartPrice(
                inputIscd  = NightFuturesCode.KOSPI200_NIGHT_NEAR,
                hourClsCode = "60",   // 1분봉
                inputDate  = today,
                inputHour  = NightFuturesCode.NIGHT_SESSION_START
            )
            result.onSuccess { candles ->
                // 야간 시간대만 필터링 (18:00 이후 또는 00:00~05:00)
                val nightCandles = candles.filter { candle ->
                    val hour = candle.time.take(2).toIntOrNull() ?: 0
                    hour >= 18 || hour < 5
                }
                // UI 업데이트 (차트 라이브러리에 전달)
                _chartData.value = nightCandles
            }.onFailure { error ->
                _errorMessage.value = error.message
            }
        }
    }
}
*/
