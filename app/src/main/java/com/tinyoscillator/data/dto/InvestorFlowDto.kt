package com.tinyoscillator.data.dto

import com.tinyoscillator.core.util.DateFormats
import com.tinyoscillator.domain.model.DailyInvestorFlow
import com.tinyoscillator.domain.model.InvestorLeg
import com.tinyoscillator.domain.model.InvestorType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * KIS FHPTJ04160001(투자자매매동향, 종목별 일자별) 응답 봉투.
 * 명세: docs/TASK_investor_volume_profile.md §6.1, §6.3.
 *
 * [KisFinancialApiResponse]와 달리 행 배열이 `output2`에 담긴다.
 */
@Serializable
data class KisInvestorFlowResponse(
    @SerialName("rt_cd") val rtCd: String = "",
    @SerialName("msg_cd") val msgCd: String = "",
    @SerialName("msg1") val msg1: String = "",
    val output2: List<Map<String, String?>>? = null
)

/**
 * `output2` 행 하나를 [DailyInvestorFlow]로 매핑한다.
 *
 * 필수 필드(일자·고가·저가·종가·거래량·주체별 매수/매도 수량·대금) 중 하나라도 결측·비숫자면
 * `null`을 반환한다. 당일 미확정 행은 투자자 필드가 `""`이라 이 경로로 걸러진다(§6.2 6항).
 * 투자자별 거래대금은 KIS 응답이 **백만원** 단위라 `Long` 승격(×1_000_000L)이 필요하다
 * (Int 범위를 조용히 넘는다. §6.3).
 */
fun Map<String, String?>.toDailyInvestorFlowOrNull(): DailyInvestorFlow? {
    val date = this["stck_bsop_date"]?.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it, DateFormats.yyyyMMdd) }.getOrNull()
    } ?: return null
    val high = parseNumericLong(this["stck_hgpr"]) ?: return null
    val low = parseNumericLong(this["stck_lwpr"]) ?: return null
    val close = parseNumericLong(this["stck_clpr"]) ?: return null
    val volume = parseNumericLong(this["acml_vol"]) ?: return null

    val individual = leg("prsn") ?: return null
    val foreign = leg("frgn") ?: return null
    val institution = leg("orgn") ?: return null

    return DailyInvestorFlow(
        date = date,
        high = high,
        low = low,
        close = close,
        volume = volume,
        flows = mapOf(
            InvestorType.INDIVIDUAL to individual,
            InvestorType.FOREIGN to foreign,
            InvestorType.INSTITUTION to institution
        )
    )
}

/** [prefix]는 `prsn`(개인) / `frgn`(외국인) / `orgn`(기관). 결측·비숫자면 null. */
private fun Map<String, String?>.leg(prefix: String): InvestorLeg? {
    val buyVolume = parseNumericLong(this["${prefix}_shnu_vol"]) ?: return null
    val buyAmountMillion = parseNumericLong(this["${prefix}_shnu_tr_pbmn"]) ?: return null
    val sellVolume = parseNumericLong(this["${prefix}_seln_vol"]) ?: return null
    val sellAmountMillion = parseNumericLong(this["${prefix}_seln_tr_pbmn"]) ?: return null
    return InvestorLeg(
        buyVolume = buyVolume,
        buyAmount = buyAmountMillion * 1_000_000L,
        sellVolume = sellVolume,
        sellAmount = sellAmountMillion * 1_000_000L
    )
}
