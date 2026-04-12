package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.data.dto.KisEstimatedEarningsResponse
import com.tinyoscillator.data.dto.mapToEstimatedEarningsInfo
import com.tinyoscillator.data.dto.EARNINGS_FORMATS
import com.tinyoscillator.data.dto.EARNINGS_LABELS
import com.tinyoscillator.data.dto.VALUATION_FORMATS
import com.tinyoscillator.data.dto.VALUATION_LABELS
import com.tinyoscillator.data.dto.formatAmount
import com.tinyoscillator.data.dto.mapToEstimatedEarningsRow
import com.tinyoscillator.data.dto.mapToPeriod
import com.tinyoscillator.domain.model.EstimatedEarningsSummary
import kotlinx.serialization.json.Json
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "EstimatedEarningsRepo"

class EstimatedEarningsRepository(
    private val kisApiClient: KisApiClient,
    private val json: Json
) {
    suspend fun getEstimatedEarnings(
        ticker: String,
        kisConfig: KisApiKeyConfig
    ): Result<EstimatedEarningsSummary> {
        if (ticker.isBlank()) {
            return Result.failure(IllegalArgumentException("종목코드가 비어있습니다."))
        }
        if (!kisConfig.isValid()) {
            return Result.failure(
                IllegalStateException("KIS API key not configured. 설정에서 KIS API 키를 입력해주세요.")
            )
        }

        return try {
            Timber.d("추정실적 조회 시작: ticker=%s", ticker)

            val queryParams = mapOf("SHT_CD" to ticker)

            val result = kisApiClient.get(
                trId = TR_ESTIMATED_EARNINGS,
                url = EP_ESTIMATED_EARNINGS,
                queryParams = queryParams,
                config = kisConfig
            ) { responseBody -> responseBody }

            val responseBody = result.getOrElse { throw it }

            val apiResponse = json.decodeFromString<KisEstimatedEarningsResponse>(responseBody)
            Timber.d("추정실적 rt_cd=%s, msg=%s", apiResponse.rtCd, apiResponse.msg1)

            if (apiResponse.rtCd != "0") {
                return Result.failure(
                    RuntimeException("KIS API 오류 [${apiResponse.msgCd}]: ${apiResponse.msg1}")
                )
            }

            val output1 = apiResponse.output1
            if (output1 == null) {
                return Result.failure(RuntimeException("추정실적 데이터가 비어있습니다."))
            }

            val info = mapToEstimatedEarningsInfo(output1, ticker)
            val earningsData = apiResponse.output2?.mapIndexed { index, item ->
                mapToEstimatedEarningsRow(
                    item,
                    EARNINGS_LABELS.getOrElse(index) { "" },
                    EARNINGS_FORMATS.getOrElse(index) { ::formatAmount }
                )
            } ?: emptyList()
            val valuationData = apiResponse.output3?.mapIndexed { index, item ->
                mapToEstimatedEarningsRow(
                    item,
                    VALUATION_LABELS.getOrElse(index) { "" },
                    VALUATION_FORMATS.getOrElse(index) { ::formatAmount }
                )
            } ?: emptyList()
            val periods = apiResponse.output4?.map { mapToPeriod(it) }?.filter { it.isNotBlank() } ?: emptyList()

            Timber.d("추정실적 매핑: output2=%d, output3=%d, periods=%d",
                earningsData.size, valuationData.size, periods.size)

            Result.success(
                EstimatedEarningsSummary(
                    info = info,
                    earningsData = earningsData,
                    valuationData = valuationData,
                    periods = periods,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "추정실적 조회 실패: %s", ticker)
            Result.failure(e)
        }
    }

    companion object {
        private const val TR_ESTIMATED_EARNINGS = "HHKST668300C0"
        private const val EP_ESTIMATED_EARNINGS = "/uapi/domestic-stock/v1/quotations/estimate-perform"
    }
}
