package com.tinyoscillator.data.dto

import com.tinyoscillator.domain.model.EstimatedEarningsInfo
import com.tinyoscillator.domain.model.EstimatedEarningsRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

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
    val rawDate = output1["estdate"]?.trim() ?: ""
    val formattedDate = if (rawDate.length == 8) {
        "${rawDate.substring(0, 4)}.${rawDate.substring(4, 6)}.${rawDate.substring(6, 8)}"
    } else rawDate

    return EstimatedEarningsInfo(
        ticker = ticker,
        stockName = output1["item_kor_nm"]?.trim() ?: "",
        analystName = output1["name1"]?.trim() ?: "",
        estimateDate = formattedDate,
        recommendation = output1["rcmd_name"]?.trim() ?: "",
        targetPrice = formatAmount(output1["capital"]?.trim() ?: ""),
    )
}

fun mapToEstimatedEarningsRow(
    item: Map<String, String?>,
    label: String,
    formatter: (String) -> String = ::formatAmount
): EstimatedEarningsRow {
    fun fmt(key: String) = formatter(item[key]?.trim() ?: "")
    return EstimatedEarningsRow(
        label = label,
        data1 = fmt("data1"),
        data2 = fmt("data2"),
        data3 = fmt("data3"),
        data4 = fmt("data4"),
        data5 = fmt("data5"),
    )
}

// ── 값 포맷터 ──

/** 억원 등 정수 금액: ".0" 제거, 천단위 쉼표 */
fun formatAmount(raw: String): String {
    if (raw.isBlank()) return ""
    val num = raw.toDoubleOrNull() ?: return raw
    return String.format(Locale.KOREA, "%,d", num.toLong())
}

/** 0.1% 단위 비율: ÷10, 소수 1자리 */
fun formatScaledRate(raw: String): String {
    if (raw.isBlank()) return ""
    val num = raw.toDoubleOrNull() ?: return raw
    return String.format(Locale.KOREA, "%,.1f", num / 10.0)
}

/** 0.1원 단위 EPS: ÷10, 정수 쉼표 */
fun formatScaledInt(raw: String): String {
    if (raw.isBlank()) return ""
    val num = raw.toDoubleOrNull() ?: return raw
    return String.format(Locale.KOREA, "%,d", (num / 10.0).toLong())
}

// ── 행 라벨 & 포맷 매핑 ──

/** output2 행 순서: 추정손익계산서 */
val EARNINGS_LABELS = listOf(
    "매출액", "매출액증감율", "영업이익", "영업이익증감율", "순이익", "순이익증감율"
)

/** output2 행별 포맷: 금액 → 증감율 → 금액 → … */
val EARNINGS_FORMATS: List<(String) -> String> = listOf(
    ::formatAmount,     // 매출액 (억원)
    ::formatScaledRate, // 매출액증감율 (0.1%)
    ::formatAmount,     // 영업이익 (억원)
    ::formatScaledRate, // 영업이익증감율 (0.1%)
    ::formatAmount,     // 순이익 (억원)
    ::formatScaledRate, // 순이익증감율 (0.1%)
)

/** output3 행 순서: 투자지표 */
val VALUATION_LABELS = listOf(
    "EBITDA", "EPS", "EPS증감율", "PER", "EV/EBITDA", "ROE", "부채비율", "이자보상배율"
)

/** output3 행별 포맷 */
val VALUATION_FORMATS: List<(String) -> String> = listOf(
    ::formatAmount,     // EBITDA (억원)
    ::formatScaledInt,  // EPS (0.1원)
    ::formatScaledRate, // EPS증감율 (0.1%)
    ::formatScaledRate, // PER (0.1배)
    ::formatScaledRate, // EV/EBITDA (0.1배)
    ::formatScaledRate, // ROE (0.1%)
    ::formatScaledRate, // 부채비율 (0.1%)
    ::formatScaledRate, // 이자보상배율 (0.1배)
)

fun mapToPeriod(item: Map<String, String?>): String {
    return item["dt"]?.trim() ?: ""
}
