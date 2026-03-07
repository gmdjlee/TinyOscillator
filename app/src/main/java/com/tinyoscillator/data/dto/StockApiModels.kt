package com.tinyoscillator.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// ka10099 - 종목 리스트
// ============================================================================

@Serializable
data class StockListResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("list") val stkList: List<StockListItem>? = null
)

@Serializable
data class StockListItem(
    @SerialName("code") val stkCd: String? = null,
    @SerialName("name") val stkNm: String? = null,
    @SerialName("marketName") val mrktNm: String? = null
)

// ============================================================================
// ka10059 - 투자자별 매매 동향
// ============================================================================

@Serializable
data class InvestorTrendResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("stk_invsr_orgn") val data: List<InvestorTrendItem>? = null
)

@Serializable
data class InvestorTrendItem(
    @SerialName("dt") val date: String? = null,
    @SerialName("frgnr_invsr") val foreignNet: Long? = null,
    @SerialName("orgn") val institutionNet: Long? = null,
    @SerialName("ind_invsr") val individualNet: Long? = null,
    @SerialName("mrkt_tot_amt") val marketCap: Long? = null
)

// ============================================================================
// ka10001 - 주식 기본 정보
// ============================================================================

@Serializable
data class StockInfoResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("stk_nm") val stkNm: String? = null,
    @SerialName("cur_prc") val curPrc: String? = null,
    @SerialName("mac") val mac: String? = null,
    @SerialName("flo_stk") val floStk: String? = null
)

// ============================================================================
// ka10081 - 일봉 차트
// ============================================================================

@Serializable
data class DailyOhlcvResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("stk_dt_pole_chart_qry") val data: List<OhlcvItem>? = null
)

@Serializable
data class OhlcvItem(
    @SerialName("dt") val date: String? = null,
    @SerialName("open_pric") val open: Int? = null,
    @SerialName("high_pric") val high: Int? = null,
    @SerialName("low_pric") val low: Int? = null,
    @SerialName("cur_prc") val close: Int? = null,
    @SerialName("trde_qty") val volume: Long? = null
)

// ============================================================================
// ka10063 - 실시간 수급 (장중 외국인/기관 순매수)
// ============================================================================

@Serializable
data class RealtimeSupplyResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null
)

@Serializable
data class RealtimeSupplyItemDto(
    @SerialName("stk_cd") val stkCd: String? = null,
    @SerialName("stk_nm") val stkNm: String? = null,
    @SerialName("cur_prc") val currentPrice: String? = null,
    @SerialName("netbid_amt") val netBuyAmount: String? = null,
    @SerialName("bid_amt") val buyAmount: String? = null,
    @SerialName("ask_amt") val sellAmount: String? = null,
    @SerialName("netbid_qty") val netBuyQuantity: String? = null,
    @SerialName("acml_trde_qty") val accumulatedVolume: String? = null
)

// ============================================================================
// API 엔드포인트 & ID 상수
// ============================================================================

object StockApiEndpoints {
    const val STOCK_LIST = "/api/dostk/stkinfo"
    const val STOCK_INFO = "/api/dostk/stkinfo"
    const val INVESTOR_TREND = "/api/dostk/stkinfo"
    const val DAILY_CHART = "/api/dostk/chart"
    const val REALTIME_SUPPLY = "/api/dostk/mrkcond"
}

object StockApiIds {
    const val STOCK_LIST = "ka10099"
    const val STOCK_INFO = "ka10001"
    const val INVESTOR_TREND = "ka10059"
    const val DAILY_CHART = "ka10081"
    const val REALTIME_SUPPLY = "ka10063"
}
