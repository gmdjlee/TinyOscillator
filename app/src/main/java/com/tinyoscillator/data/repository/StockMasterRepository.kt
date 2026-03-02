package com.tinyoscillator.data.repository

import android.util.Log
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.dto.StockApiEndpoints
import com.tinyoscillator.data.dto.StockApiIds
import com.tinyoscillator.data.dto.StockListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockMasterRepository @Inject constructor(
    private val stockMasterDao: StockMasterDao,
    private val apiClient: KiwoomApiClient,
    private val json: Json
) {
    /**
     * 종목 마스터 DB가 비어있으면 Kiwoom API로 전체 종목을 조회하여 저장.
     */
    suspend fun populateIfEmpty(config: KiwoomApiKeyConfig) = withContext(Dispatchers.IO) {
        val count = stockMasterDao.getCount()
        if (count > 0) {
            Log.d(TAG, "종목 마스터 DB 이미 존재: ${count}건")
            return@withContext
        }

        if (!config.isValid()) {
            Log.w(TAG, "API 키가 설정되지 않아 종목 마스터를 채울 수 없습니다")
            return@withContext
        }

        Log.d(TAG, "종목 마스터 DB 비어있음 → API에서 전체 종목 조회 시작")

        val body = mapOf("mrkt_tp" to "0")  // 전체 시장

        val result = apiClient.call(
            apiId = StockApiIds.STOCK_LIST,
            url = StockApiEndpoints.STOCK_LIST,
            body = body,
            config = config
        ) { responseJson ->
            json.decodeFromString<StockListResponse>(responseJson)
        }

        result.onSuccess { response ->
            val items = response.stkList ?: emptyList()
            val now = System.currentTimeMillis()
            val entities = items.mapNotNull { item ->
                val ticker = item.stkCd?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = item.stkNm?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                StockMasterEntity(
                    ticker = ticker,
                    name = name,
                    market = item.mrktNm ?: "",
                    lastUpdated = now
                )
            }

            if (entities.isNotEmpty()) {
                stockMasterDao.insertAll(entities)
                Log.d(TAG, "종목 마스터 DB 저장 완료: ${entities.size}건")
            }
        }.onFailure { error ->
            Log.e(TAG, "종목 마스터 조회 실패: ${error.message}")
        }
    }

    /**
     * 로컬 DB에서 종목 검색 (Flow).
     */
    fun searchStocks(query: String): Flow<List<StockMasterEntity>> {
        return stockMasterDao.searchStocks(query)
    }

    /**
     * 종목 마스터 DB 건수.
     */
    suspend fun getCount(): Int = stockMasterDao.getCount()

    companion object {
        private const val TAG = "StockMasterRepo"
    }
}
