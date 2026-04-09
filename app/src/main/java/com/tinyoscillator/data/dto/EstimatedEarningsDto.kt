package com.tinyoscillator.data.dto

import com.tinyoscillator.domain.model.EstimatedEarningsInfo
import com.tinyoscillator.domain.model.EstimatedEarningsRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KisEstimatedEarningsResponse(
    @SerialName("rt_cd") val rtCd: String = "",
    @SerialName("msg_cd") val msgCd: String = "",
    @SerialName("msg1") val msg1: String = "",
    val output1: Map<String, String?>? = null,
    val output2: List<Map<String, String?>>? = null,
    val output3: List<Map<String, String?>>? = null,
    val output4: List<Map<String, String?>>? = null,
)

fun mapToEstimatedEarningsInfo(
    output1: Map<String, String?>,
    ticker: String
): EstimatedEarningsInfo {
    return EstimatedEarningsInfo(
        ticker = ticker,
        stockName = output1["item_kor_nm"]?.trim() ?: "",
        currentPrice = output1["name1"]?.trim() ?: "",
        priceChange = output1["name2"]?.trim() ?: "",
        changeSign = output1["estdate"]?.trim() ?: "",
        changeRate = output1["rcmd_name"]?.trim() ?: "",
        volume = output1["capital"]?.trim() ?: "",
    )
}

fun mapToEstimatedEarningsRow(item: Map<String, String?>): EstimatedEarningsRow {
    return EstimatedEarningsRow(
        data1 = item["data1"]?.trim() ?: "",
        data2 = item["data2"]?.trim() ?: "",
        data3 = item["data3"]?.trim() ?: "",
        data4 = item["data4"]?.trim() ?: "",
        data5 = item["data5"]?.trim() ?: "",
    )
}

fun mapToPeriod(item: Map<String, String?>): String {
    return item["dt"]?.trim() ?: ""
}
