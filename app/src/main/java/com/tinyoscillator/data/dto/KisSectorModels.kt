package com.tinyoscillator.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// KIS inquire-daily-indexchartprice (TR_ID=FHKUP03500100) — 국내주식업종기간별시세(일/주/월/년)
// ============================================================================

@Serializable
data class KisSectorIndexChartResponse(
    @SerialName("rt_cd") val rtCd: String? = null,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output1") val output1: KisSectorIndexChartHeader? = null,
    @SerialName("output2") val output2: List<KisSectorIndexCandleItem>? = null,
)

@Serializable
data class KisSectorIndexChartHeader(
    /** 현재가 (업종지수) */
    @SerialName("bstp_nmix_prpr") val currentPrice: String? = null,
    /** 전일대비 */
    @SerialName("bstp_nmix_prdy_vrss") val priorDiff: String? = null,
    /** 전일대비율 */
    @SerialName("prdy_ctrt") val priorRate: String? = null,
    /** 전일대비부호 (1~5: 상한/상승/보합/하락/하한) */
    @SerialName("prdy_vrss_sign") val priorSign: String? = null,
)

@Serializable
data class KisSectorIndexCandleItem(
    /** YYYYMMDD */
    @SerialName("stck_bsop_date") val date: String? = null,
    /** 시가 */
    @SerialName("bstp_nmix_oprc") val open: String? = null,
    /** 고가 */
    @SerialName("bstp_nmix_hgpr") val high: String? = null,
    /** 저가 */
    @SerialName("bstp_nmix_lwpr") val low: String? = null,
    /** 종가 */
    @SerialName("bstp_nmix_prpr") val close: String? = null,
    /** 누적거래량 */
    @SerialName("acml_vol") val volume: String? = null,
)
