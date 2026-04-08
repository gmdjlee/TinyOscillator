package com.tinyoscillator.data.dto

import com.tinyoscillator.domain.model.InvestOpinion

fun mapToInvestOpinion(item: Map<String, String?>): InvestOpinion? {
    val date = item["stck_bsop_date"] ?: return null
    val firmName = item["mbcr_name"]?.trim() ?: return null
    if (firmName.isBlank()) return null
    return InvestOpinion(
        date = date,
        firmName = firmName,
        opinion = item["invt_opnn"]?.trim() ?: "",
        opinionCode = item["invt_opnn_cls_code"]?.trim() ?: "",
        targetPrice = parseNumericLong(item["hts_goal_prc"]),
        currentPrice = parseNumericLong(item["stck_prpr"]),
        changeSign = item["stck_nday_esdg"]?.trim() ?: "",
        changeAmount = parseNumericLong(item["stck_nday_sdpr"]),
    )
}
