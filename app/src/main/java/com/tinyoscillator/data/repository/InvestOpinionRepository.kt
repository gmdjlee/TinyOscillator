package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.data.dto.KisFinancialApiResponse
import com.tinyoscillator.data.dto.mapToInvestOpinion
import com.tinyoscillator.domain.model.InvestOpinion
import com.tinyoscillator.domain.model.InvestOpinionSummary
import kotlinx.serialization.json.Json
import android.util.Log
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "InvestOpinionRepo"

class InvestOpinionRepository(
    private val kisApiClient: KisApiClient,
    private val json: Json
) {
    suspend fun getInvestOpinions(
        ticker: String,
        stockName: String,
        kisConfig: KisApiKeyConfig
    ): Result<InvestOpinionSummary> {
        if (ticker.isBlank()) {
            return Result.failure(IllegalArgumentException("종목코드가 비어있습니다."))
        }
        if (!kisConfig.isValid()) {
            return Result.failure(
                IllegalStateException("KIS API key not configured. 설정에서 KIS API 키를 입력해주세요.")
            )
        }

        return try {
            Log.w(TAG, "투자의견 조회 시작: ticker=$ticker, kisValid=${kisConfig.isValid()}")

            val today = LocalDate.now()
            val sixMonthsAgo = today.minusMonths(6)
            val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

            val queryParams = mapOf(
                "FID_COND_MRKT_DIV_CODE" to "J",
                "FID_COND_SCR_DIV_CODE" to "16634",
                "FID_INPUT_ISCD" to ticker,
                "FID_DIV_CLS_CODE" to "0",
                "FID_INPUT_DATE_1" to sixMonthsAgo.format(fmt),
                "FID_INPUT_DATE_2" to today.format(fmt)
            )

            val result = kisApiClient.get(
                trId = TR_INVEST_OPINION,
                url = EP_INVEST_OPINION,
                queryParams = queryParams,
                config = kisConfig
            ) { responseBody ->
                Log.w(TAG, "투자의견 raw response (첫 500자): ${responseBody.take(500)}")
                responseBody
            }

            val responseBody = result.getOrElse { throw it }
            Log.w(TAG, "투자의견 HTTP 성공, 파싱 시작")

            val apiResponse = json.decodeFromString<KisFinancialApiResponse>(responseBody)
            Log.w(TAG, "투자의견 rt_cd=${apiResponse.rtCd}, msg_cd=${apiResponse.msgCd}, msg1=${apiResponse.msg1}")
            Log.w(TAG, "투자의견 output=${apiResponse.output?.size ?: -1}, output1=${apiResponse.output1?.size ?: -1}")

            if (apiResponse.rtCd != "0") {
                return Result.failure(
                    RuntimeException("KIS API 오류 [${apiResponse.msgCd}]: ${apiResponse.msg1}")
                )
            }

            val output = apiResponse.actualOutput
            if (output.isNullOrEmpty()) {
                return Result.failure(RuntimeException("투자의견 데이터가 비어있습니다 (output=null)"))
            }

            Log.w(TAG, "투자의견 output 첫 항목 keys: ${output.first().keys}")
            val opinions = output.mapNotNull { mapToInvestOpinion(it) }
            Log.w(TAG, "투자의견 매핑 결과: raw=${output.size}, mapped=${opinions.size}")

            val buyCount = opinions.count { isBuyOpinion(it.opinion, it.opinionCode) }
            val sellCount = opinions.count { isSellOpinion(it.opinion, it.opinionCode) }
            val holdCount = opinions.size - buyCount - sellCount

            val targetPrices = opinions.mapNotNull { it.targetPrice }.filter { it > 0 }
            val avgTarget = if (targetPrices.isNotEmpty()) targetPrices.average().toLong() else null
            val currentPrice = opinions.firstOrNull()?.currentPrice

            Result.success(
                InvestOpinionSummary(
                    ticker = ticker,
                    stockName = stockName,
                    opinions = opinions,
                    buyCount = buyCount,
                    holdCount = holdCount,
                    sellCount = sellCount,
                    avgTargetPrice = avgTarget,
                    currentPrice = currentPrice,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "투자의견 조회 실패: %s", ticker)
            Result.failure(e)
        }
    }

    companion object {
        private const val TR_INVEST_OPINION = "FHKST663400C0"
        private const val EP_INVEST_OPINION = "/uapi/domestic-stock/v1/quotations/invest-opinion"

        private fun isBuyOpinion(opinion: String, code: String): Boolean {
            val lower = opinion.lowercase()
            return lower.contains("매수") || lower.contains("buy") ||
                    lower.contains("outperform") || lower.contains("overweight") ||
                    code in listOf("1", "2", "01", "02")
        }

        private fun isSellOpinion(opinion: String, code: String): Boolean {
            val lower = opinion.lowercase()
            return lower.contains("매도") || lower.contains("sell") ||
                    lower.contains("underperform") || lower.contains("underweight") ||
                    code in listOf("4", "5", "04", "05")
        }
    }
}
