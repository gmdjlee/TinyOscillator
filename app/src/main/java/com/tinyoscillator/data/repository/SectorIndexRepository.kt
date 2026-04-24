package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.database.dao.SectorIndexCandleDao
import com.tinyoscillator.core.database.dao.SectorMasterDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.entity.SectorIndexCandleEntity
import com.tinyoscillator.core.database.entity.SectorMasterEntity
import com.tinyoscillator.data.dto.KisSearchStockInfoResponse
import com.tinyoscillator.data.dto.KisSectorIndexChartResponse
import com.tinyoscillator.domain.model.SectorChartPeriod
import com.tinyoscillator.domain.model.SectorIndex
import com.tinyoscillator.domain.model.SectorIndexCandle
import com.tinyoscillator.domain.model.SectorIndexChart
import com.tinyoscillator.domain.model.SectorIndexQuote
import com.tinyoscillator.domain.model.SectorLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * KIS 종목정보(CTPF1002R) 응답의 업종 코드·명을 수집해 마스터로 저장하고,
 * KIS 업종지수 일별 차트(FHPUP02140000)를 조회/캐시한다.
 *
 * 레이트 리밋 / 비용 고려:
 *  - [KisApiClient]가 이미 500ms 글로벌 rate limit + 3실패 5분 서킷 브레이커를 수행.
 *  - 마스터 갱신은 [REFRESH_TTL_MS] (30일) 이내면 스킵. 호출 한 번당 KRX 섹터 수(≈40~60건)
 *    만큼 KIS를 두드리므로, 사용자 트리거 외 자동 주기는 설정하지 않는다.
 *  - 차트 조회는 요청 전 Room 캐시(`sector_index_candle`)를 먼저 확인하고
 *    cachedAt이 [CHART_CACHE_TTL_MS] (6시간) 이내면 네트워크 호출을 건너뛴다.
 */
@Singleton
class SectorIndexRepository @Inject constructor(
    private val sectorMasterDao: SectorMasterDao,
    private val candleDao: SectorIndexCandleDao,
    private val stockMasterDao: StockMasterDao,
    private val kisApiClient: KisApiClient,
    private val json: Json,
) {

    fun observeSectors(): Flow<List<SectorIndex>> =
        sectorMasterDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeSectorsByLevel(level: SectorLevel): Flow<List<SectorIndex>> =
        sectorMasterDao.observeByLevel(level.code).map { list -> list.map { it.toDomain() } }

    suspend fun lastMasterUpdate(): Long? = withContext(Dispatchers.IO) {
        sectorMasterDao.lastUpdatedAt()
    }

    suspend fun sectorCount(): Int = withContext(Dispatchers.IO) {
        sectorMasterDao.count()
    }

    /**
     * KRX 섹터별 대표 종목 1건씩 KIS 종목정보를 조회해 업종 코드·명을 dedupe 저장.
     *
     * 빈 상태에서 호출하면 KRX 섹터 수 × 500ms 만큼 소요 (≈20~30초).
     * 캐시가 TTL 이내면 [force]가 false인 경우 즉시 성공 반환.
     */
    suspend fun refreshSectorMaster(
        kisConfig: KisApiKeyConfig,
        force: Boolean = false,
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!kisConfig.isValid()) {
            return@withContext Result.failure(
                IllegalStateException("KIS API 키가 설정되지 않았습니다. 설정 화면에서 등록해 주세요.")
            )
        }

        val now = System.currentTimeMillis()
        val lastUpdated = sectorMasterDao.lastUpdatedAt()
        val cachedCount = sectorMasterDao.count()
        if (!force && lastUpdated != null && cachedCount > 0 &&
            now - lastUpdated < REFRESH_TTL_MS
        ) {
            Timber.d("업종 마스터 캐시 유효 (%.1f일 남음)",
                (REFRESH_TTL_MS - (now - lastUpdated)) / (24 * 60 * 60 * 1000.0))
            return@withContext Result.success(cachedCount)
        }

        val pickedTickers = pickRepresentativeTickers()
        if (pickedTickers.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("종목 마스터가 비어 있습니다. 먼저 종목 DB를 갱신해 주세요.")
            )
        }

        val collected = LinkedHashMap<String, SectorMasterEntity>()
        var success = 0
        var failure = 0
        for (ticker in pickedTickers) {
            val result = fetchStockInfo(ticker, kisConfig)
            result.fold(
                onSuccess = { output ->
                    success++
                    collectSectorRows(output, now, collected)
                },
                onFailure = { err ->
                    failure++
                    Timber.w("KIS search-stock-info 실패: %s / %s", ticker, err.message)
                }
            )
        }

        Timber.i("업종 마스터 수집: 성공 %d / 실패 %d / unique=%d", success, failure, collected.size)

        if (collected.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("업종 정보를 가져오지 못했습니다. KIS API 응답을 확인해 주세요.")
            )
        }

        sectorMasterDao.replaceAll(collected.values.toList())
        Result.success(collected.size)
    }

    /**
     * 업종지수 일봉 캔들 조회. 캐시가 있고 TTL 이내면 네트워크 호출을 생략한다.
     */
    suspend fun getSectorIndexChart(
        code: String,
        period: SectorChartPeriod,
        kisConfig: KisApiKeyConfig,
    ): Result<SectorIndexChart> = withContext(Dispatchers.IO) {
        if (!kisConfig.isValid()) {
            return@withContext Result.failure(
                IllegalStateException("KIS API 키가 설정되지 않았습니다. 설정 화면에서 등록해 주세요.")
            )
        }

        val master = sectorMasterDao.getByCode(code)
            ?: return@withContext Result.failure(
                IllegalStateException("업종 정보를 찾을 수 없습니다: $code")
            )

        val today = LocalDate.now()
        val from = today.minusDays(period.days.toLong())
        val fromStr = from.format(DATE_FMT)
        val toStr = today.format(DATE_FMT)

        // 캐시 먼저 확인 (일봉만 해당 — 주/월은 요청마다 갱신)
        val latestCachedAt = candleDao.getLatestCachedAt(code)
        val cacheFresh = period == SectorChartPeriod.DAILY &&
            latestCachedAt != null &&
            System.currentTimeMillis() - latestCachedAt < CHART_CACHE_TTL_MS
        if (cacheFresh) {
            val cached = candleDao.getRange(code, fromStr, toStr)
            if (cached.isNotEmpty()) {
                Timber.d("업종지수 캐시 반환: %s (%d건)", code, cached.size)
                return@withContext Result.success(
                    SectorIndexChart(
                        code = code,
                        name = master.name,
                        quote = null, // 캐시 반환 시 실시간 quote는 스킵
                        candles = cached.map { it.toDomain() },
                    )
                )
            }
        }

        val iscd = normalizeIscd(code)
        val queryParams = mapOf(
            "FID_COND_MRKT_DIV_CODE" to "U",
            "FID_INPUT_ISCD" to iscd,
            "FID_INPUT_DATE_1" to fromStr,
            "FID_INPUT_DATE_2" to toStr,
            "FID_PERIOD_DIV_CODE" to period.apiCode,
        )

        val apiResult = try {
            kisApiClient.get(
                trId = TR_SECTOR_INDEX_CHART,
                url = EP_SECTOR_INDEX_CHART,
                queryParams = queryParams,
                config = kisConfig,
            ) { body -> json.decodeFromString<KisSectorIndexChartResponse>(body) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "업종지수 차트 조회 실패: %s", code)
            return@withContext Result.failure(e)
        }

        val response = apiResult.getOrElse { return@withContext Result.failure(it) }

        if (response.rtCd != "0") {
            return@withContext Result.failure(
                RuntimeException("KIS 업종지수 응답 오류 [${response.msgCd}]: ${response.msg1}")
            )
        }

        val candles = response.output2.orEmpty()
            .mapNotNull { item ->
                val date = item.date.orEmpty()
                if (date.length != 8) return@mapNotNull null
                SectorIndexCandle(
                    date = date,
                    open = item.open?.toDoubleOrNull() ?: return@mapNotNull null,
                    high = item.high?.toDoubleOrNull() ?: return@mapNotNull null,
                    low = item.low?.toDoubleOrNull() ?: return@mapNotNull null,
                    close = item.close?.toDoubleOrNull() ?: return@mapNotNull null,
                    volume = item.volume?.toLongOrNull() ?: 0L,
                )
            }
            .sortedBy { it.date }

        // 일봉만 캐시 (주/월은 요청마다 재계산)
        if (period == SectorChartPeriod.DAILY && candles.isNotEmpty()) {
            val now = System.currentTimeMillis()
            candleDao.insertAll(candles.map {
                SectorIndexCandleEntity(
                    code = code,
                    date = it.date,
                    open = it.open,
                    high = it.high,
                    low = it.low,
                    close = it.close,
                    volume = it.volume,
                    cachedAt = now,
                )
            })
        }

        val quote = response.output1?.let { h ->
            val price = h.currentPrice?.toDoubleOrNull()
            val diff = h.priorDiff?.toDoubleOrNull() ?: 0.0
            val rate = h.priorRate?.toDoubleOrNull() ?: 0.0
            if (price != null) SectorIndexQuote(price, diff, rate) else null
        }

        Result.success(
            SectorIndexChart(
                code = code,
                name = master.name,
                quote = quote,
                candles = candles,
            )
        )
    }

    // ========== Private helpers ==========

    /**
     * KRX 섹터별로 대표 종목 1개씩 뽑는다. KIS API 호출 수를 섹터 개수로 한정.
     * stock_master.sector가 빈 종목은 제외.
     */
    private suspend fun pickRepresentativeTickers(): List<String> {
        val sectors = stockMasterDao.getAllSectors()
        val result = mutableListOf<String>()
        for (sector in sectors) {
            val tickers = stockMasterDao.getTickersBySector(sector, 1)
            if (tickers.isNotEmpty()) result += tickers.first()
        }
        return result
    }

    private suspend fun fetchStockInfo(
        ticker: String,
        kisConfig: KisApiKeyConfig,
    ): Result<com.tinyoscillator.data.dto.KisSearchStockInfoOutput> {
        return try {
            val params = mapOf(
                "PDNO" to ticker,
                "PRDT_TYPE_CD" to "300",
            )
            val res = kisApiClient.get(
                trId = TR_SEARCH_STOCK_INFO,
                url = EP_SEARCH_STOCK_INFO,
                queryParams = params,
                config = kisConfig,
            ) { body -> json.decodeFromString<KisSearchStockInfoResponse>(body) }

            val resp = res.getOrElse { return Result.failure(it) }
            if (resp.rtCd != "0" || resp.output == null) {
                return Result.failure(
                    RuntimeException("KIS search-stock-info 오류 [${resp.msgCd}]: ${resp.msg1}")
                )
            }
            Result.success(resp.output)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun collectSectorRows(
        output: com.tinyoscillator.data.dto.KisSearchStockInfoOutput,
        now: Long,
        into: LinkedHashMap<String, SectorMasterEntity>,
    ) {
        val large = output.idxBztpLclsCd to output.idxBztpLclsCdName
        val middle = output.idxBztpMclsCd to output.idxBztpMclsCdName
        val small = output.idxBztpSclsCd to output.idxBztpSclsCdName

        if (!large.first.isNullOrBlank() && !large.second.isNullOrBlank()) {
            into.putIfAbsent(
                large.first!!,
                SectorMasterEntity(
                    code = large.first!!,
                    name = large.second!!,
                    level = SectorLevel.LARGE.code,
                    parentCode = null,
                    lastUpdated = now,
                )
            )
        }
        if (!middle.first.isNullOrBlank() && !middle.second.isNullOrBlank()) {
            into.putIfAbsent(
                middle.first!!,
                SectorMasterEntity(
                    code = middle.first!!,
                    name = middle.second!!,
                    level = SectorLevel.MIDDLE.code,
                    parentCode = large.first,
                    lastUpdated = now,
                )
            )
        }
        if (!small.first.isNullOrBlank() && !small.second.isNullOrBlank()) {
            into.putIfAbsent(
                small.first!!,
                SectorMasterEntity(
                    code = small.first!!,
                    name = small.second!!,
                    level = SectorLevel.SMALL.code,
                    parentCode = middle.first ?: large.first,
                    lastUpdated = now,
                )
            )
        }
    }

    /**
     * KIS 응답의 업종 코드가 3자리일 경우 4자리로 zero-pad. 숫자 외 문자열은 그대로 반환.
     * 지수 차트 엔드포인트는 FID_INPUT_ISCD에 4자리 업종코드를 기대한다.
     */
    private fun normalizeIscd(code: String): String {
        val trimmed = code.trim()
        if (trimmed.all { it.isDigit() } && trimmed.length == 3) {
            return "0$trimmed"
        }
        return trimmed
    }

    private fun SectorMasterEntity.toDomain(): SectorIndex =
        SectorIndex(code, name, SectorLevel.fromCode(level), parentCode)

    private fun SectorIndexCandleEntity.toDomain(): SectorIndexCandle =
        SectorIndexCandle(date, open, high, low, close, volume)

    companion object {
        private const val TR_SEARCH_STOCK_INFO = "CTPF1002R"
        private const val EP_SEARCH_STOCK_INFO = "/uapi/domestic-stock/v1/quotations/search-stock-info"

        private const val TR_SECTOR_INDEX_CHART = "FHPUP02140000"
        private const val EP_SECTOR_INDEX_CHART = "/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice"

        private const val REFRESH_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30일
        private const val CHART_CACHE_TTL_MS = 6L * 60 * 60 * 1000 // 6시간

        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
