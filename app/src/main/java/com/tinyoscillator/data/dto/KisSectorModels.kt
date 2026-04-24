package com.tinyoscillator.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// KIS search-stock-info (TR_ID=CTPF1002R) — 주식기본조회
// 응답에서 업종 분류 코드·명을 추출하기 위한 최소 필드 집합.
// KIS 응답 필드는 다수이나 여기서는 업종·종목명·시장 관련만 파싱.
// ============================================================================

@Serializable
data class KisSearchStockInfoResponse(
    @SerialName("rt_cd") val rtCd: String? = null,
    @SerialName("msg_cd") val msgCd: String? = null,
    @SerialName("msg1") val msg1: String? = null,
    @SerialName("output") val output: KisSearchStockInfoOutput? = null,
)

@Serializable
data class KisSearchStockInfoOutput(
    @SerialName("pdno") val pdno: String? = null,
    @SerialName("prdt_name") val prdtName: String? = null,
    /** 지수 업종 대분류 코드 */
    @SerialName("idx_bztp_lcls_cd") val idxBztpLclsCd: String? = null,
    @SerialName("idx_bztp_lcls_cd_name") val idxBztpLclsCdName: String? = null,
    /** 지수 업종 중분류 코드 */
    @SerialName("idx_bztp_mcls_cd") val idxBztpMclsCd: String? = null,
    @SerialName("idx_bztp_mcls_cd_name") val idxBztpMclsCdName: String? = null,
    /** 지수 업종 소분류 코드 */
    @SerialName("idx_bztp_scls_cd") val idxBztpSclsCd: String? = null,
    @SerialName("idx_bztp_scls_cd_name") val idxBztpSclsCdName: String? = null,
    /** 업종 한글 종목명 (보조 필드) */
    @SerialName("bstp_kor_isnm") val bstpKorIsnm: String? = null,
)

// ============================================================================
// KIS inquire-daily-indexchartprice (TR_ID=FHPUP02140000) — 국내업종 일자별 지수
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
