package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.data.dto.StockApiEndpoints
import com.tinyoscillator.data.dto.StockApiIds
import com.tinyoscillator.data.dto.StockListItem
import com.tinyoscillator.core.util.KoreanUtils
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
     * @return 저장된 종목 수 (이미 데이터가 있으면 -1)
     * @throws IllegalStateException API 키 미설정
     * @throws Exception API 호출 또는 DB 저장 실패
     */
    suspend fun populateIfEmpty(config: KiwoomApiKeyConfig): Int = withContext(Dispatchers.IO) {
        val count = stockMasterDao.getCount()
        if (count > 0) {
            Timber.d("종목 마스터 DB 이미 존재: %d건", count)
            return@withContext -1
        }

        fetchAndInsertStocks(config)
    }

    /**
     * API에서 종목 목록을 조회하여 DB에 저장.
     * @return 저장된 종목 수
     */
    private suspend fun fetchAndInsertStocks(config: KiwoomApiKeyConfig): Int {
        if (!config.isValid()) {
            throw IllegalStateException("API 키가 설정되지 않았습니다. 설정에서 Kiwoom API 키를 입력해주세요.")
        }

        Timber.d("종목 마스터 API에서 전체 종목 조회 시작")

        val marketTypes = listOf("0" to "KOSPI", "10" to "KOSDAQ")
        val allItems = mutableListOf<StockListItem>()
        val errors = mutableListOf<String>()

        for ((mrktTp, label) in marketTypes) {
            val body = mapOf("mrkt_tp" to mrktTp)
            val result = apiClient.call(
                apiId = StockApiIds.STOCK_LIST,
                url = StockApiEndpoints.STOCK_LIST,
                body = body,
                config = config
            ) { responseJson ->
                Timber.d("ka10099 raw response (%s, first 2000 chars): %s",
                    label, responseJson.take(2000))
                json.decodeFromString<StockListResponse>(responseJson)
            }

            result.onSuccess { response ->
                val items = response.stkList ?: emptyList()
                allItems.addAll(items)
                Timber.d("%s 종목 조회: %d건", label, items.size)
            }.onFailure { error ->
                Timber.w("%s 종목 조회 실패: %s", label, error.message)
                errors.add("$label: ${error.message}")
            }
        }

        if (allItems.isEmpty() && errors.isNotEmpty()) {
            throw RuntimeException("종목 조회 실패: ${errors.joinToString("; ")}")
        }

        val now = System.currentTimeMillis()
        val entities = allItems.mapNotNull { item ->
            val ticker = item.stkCd?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = item.stkNm?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StockMasterEntity(
                ticker = ticker,
                name = name,
                market = item.mrktNm ?: "",
                sector = item.sector ?: "",
                initialConsonants = KoreanUtils.extractChosung(name),
                lastUpdated = now
            )
        }

        if (entities.isEmpty()) {
            Timber.w("종목 마스터 API 응답에 유효한 종목이 없습니다")
            return 0
        }

        stockMasterDao.insertAll(entities)
        Timber.d("종목 마스터 DB 저장 완료: %d건", entities.size)
        return entities.size
    }

    /**
     * 로컬 DB에서 종목 검색 (Flow) — 기존 호환용.
     */
    fun searchStocks(query: String): Flow<List<StockMasterEntity>> {
        return stockMasterDao.searchStocks(query)
    }

    /**
     * 초성/이름/티커 통합 검색 (suspend).
     * 초성 전용 쿼리 감지 시 초성 컬럼으로 검색, 그 외 텍스트 검색.
     */
    suspend fun searchWithChosung(query: String): List<StockMasterEntity> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        if (KoreanUtils.isChosungOnly(q)) stockMasterDao.searchByChosung(q)
        else stockMasterDao.searchByText(q)
    }

    /** 티커 단건 조회 */
    suspend fun getByTicker(ticker: String): StockMasterEntity? = withContext(Dispatchers.IO) {
        stockMasterDao.getByTicker(ticker)
    }

    /**
     * 기존 데이터에 초성 컬럼 백필.
     * Migration 후 최초 1회 실행하면 기존 종목에도 초성 검색이 활성화됨.
     */
    suspend fun backfillChosung() = withContext(Dispatchers.IO) {
        val all = stockMasterDao.getAll()
        val needUpdate = all.filter { it.initialConsonants.isBlank() }
        if (needUpdate.isEmpty()) return@withContext
        val updated = needUpdate.map { it.copy(initialConsonants = KoreanUtils.extractChosung(it.name)) }
        stockMasterDao.insertAll(updated)
        Timber.d("초성 백필 완료: %d건", updated.size)
    }

    /**
     * 종목 마스터 DB를 강제로 갱신 (API 조회 후 교체).
     * API 실패 시 기존 데이터를 보존한다.
     * @return 갱신된 종목 수
     */
    suspend fun forceRefresh(config: KiwoomApiKeyConfig): Int = withContext(Dispatchers.IO) {
        // API 조회를 먼저 수행 — 실패 시 기존 데이터 보존
        val insertedCount = fetchAndInsertStocksWithReplace(config)
        insertedCount
    }

    /**
     * API에서 종목 목록을 조회하여 DB를 교체 (삭제 후 삽입).
     * API 조회 성공 후에만 기존 데이터를 삭제한다.
     */
    private suspend fun fetchAndInsertStocksWithReplace(config: KiwoomApiKeyConfig): Int {
        if (!config.isValid()) {
            throw IllegalStateException("API 키가 설정되지 않았습니다. 설정에서 Kiwoom API 키를 입력해주세요.")
        }

        Timber.d("종목 마스터 강제 갱신 시작")

        val marketTypes = listOf("0" to "KOSPI", "10" to "KOSDAQ")
        val allItems = mutableListOf<StockListItem>()
        val errors = mutableListOf<String>()

        for ((mrktTp, label) in marketTypes) {
            val body = mapOf("mrkt_tp" to mrktTp)
            val result = apiClient.call(
                apiId = StockApiIds.STOCK_LIST,
                url = StockApiEndpoints.STOCK_LIST,
                body = body,
                config = config
            ) { responseJson ->
                Timber.d("ka10099 raw response (%s, first 2000 chars): %s",
                    label, responseJson.take(2000))
                json.decodeFromString<StockListResponse>(responseJson)
            }

            result.onSuccess { response ->
                val items = response.stkList ?: emptyList()
                allItems.addAll(items)
                Timber.d("%s 종목 조회: %d건", label, items.size)
            }.onFailure { error ->
                Timber.w("%s 종목 조회 실패: %s", label, error.message)
                errors.add("$label: ${error.message}")
            }
        }

        if (allItems.isEmpty() && errors.isNotEmpty()) {
            throw RuntimeException("종목 조회 실패: ${errors.joinToString("; ")}")
        }

        val now = System.currentTimeMillis()
        val entities = allItems.mapNotNull { item ->
            val ticker = item.stkCd?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = item.stkNm?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            StockMasterEntity(
                ticker = ticker,
                name = name,
                market = item.mrktNm ?: "",
                sector = item.sector ?: "",
                initialConsonants = KoreanUtils.extractChosung(name),
                lastUpdated = now
            )
        }

        if (entities.isEmpty()) {
            if (errors.isNotEmpty()) {
                throw RuntimeException("종목 조회 부분 실패: ${errors.joinToString("; ")}")
            }
            return 0
        }

        // API 조회 성공 후에만 기존 데이터 교체 (원자적 트랜잭션)
        stockMasterDao.replaceAll(entities)
        Timber.d("종목 마스터 DB 갱신 완료: %d건", entities.size)
        return entities.size
    }

    /**
     * 종목 마스터 DB 건수.
     */
    suspend fun getCount(): Int = stockMasterDao.getCount()

}
