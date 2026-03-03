package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.dto.StockApiEndpoints
import com.tinyoscillator.data.dto.StockApiIds
import com.tinyoscillator.data.dto.StockListItem
import com.tinyoscillator.data.dto.StockListResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
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
            Timber.d("종목 마스터 DB 이미 존재: %d건", count)
            return@withContext
        }

        if (!config.isValid()) {
            Timber.w("API 키가 설정되지 않아 종목 마스터를 채울 수 없습니다")
            return@withContext
        }

        Timber.d("종목 마스터 DB 비어있음 → API에서 전체 종목 조회 시작")

        val marketTypes = listOf("0" to "KOSPI", "10" to "KOSDAQ")
        val allItems = mutableListOf<StockListItem>()

        for ((mrktTp, label) in marketTypes) {
            val body = mapOf("mrkt_tp" to mrktTp)
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
                allItems.addAll(items)
                Timber.d("%s 종목 조회: %d건", label, items.size)
            }.onFailure { error ->
                Timber.w("%s 종목 조회 실패: %s", label, error.message)
            }
        }

        val now = System.currentTimeMillis()
        val entities = allItems.mapNotNull { item ->
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
            try {
                stockMasterDao.insertAll(entities)
                Timber.d("종목 마스터 DB 저장 완료: %d건", entities.size)
            } catch (e: Exception) {
                Timber.w(e, "종목 마스터 DB 저장 실패: %d건", entities.size)
            }
        } else {
            Timber.w("종목 마스터 API 응답에 유효한 종목이 없습니다")
        }
    }

    /**
     * 로컬 DB에서 종목 검색 (Flow).
     */
    fun searchStocks(query: String): Flow<List<StockMasterEntity>> {
        return stockMasterDao.searchStocks(query)
    }

    /**
     * 종목 마스터 DB를 강제로 갱신 (삭제 후 재조회).
     */
    suspend fun forceRefresh(config: KiwoomApiKeyConfig) = withContext(Dispatchers.IO) {
        stockMasterDao.deleteAll()
        populateIfEmpty(config)
    }

    /**
     * 종목 마스터 DB 건수.
     */
    suspend fun getCount(): Int = stockMasterDao.getCount()

}
