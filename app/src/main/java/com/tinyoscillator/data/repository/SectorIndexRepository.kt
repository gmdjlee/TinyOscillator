package com.tinyoscillator.data.repository

import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.database.dao.SectorIndexCandleDao
import com.tinyoscillator.core.database.dao.SectorMasterDao
import com.tinyoscillator.core.database.entity.SectorIndexCandleEntity
import com.tinyoscillator.data.dto.KisSectorIndexChartResponse
import com.tinyoscillator.data.seed.KisSectorCodeSeed
import com.tinyoscillator.domain.model.SectorChartPeriod
import com.tinyoscillator.domain.model.SectorIndex
import com.tinyoscillator.domain.model.SectorIndexCandle
import com.tinyoscillator.domain.model.SectorIndexChart
import com.tinyoscillator.domain.model.SectorIndexQuote
import com.tinyoscillator.domain.model.SectorLevel
import com.tinyoscillator.core.database.entity.SectorMasterEntity
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
 * KIS 업종분류코드 마스터(정적 시드)와 KIS 업종지수 일별 차트(TR_ID=FHKUP03500100)를 제공한다.
 *
 * 업종 목록은 [KisSectorCodeSeed]에서 가져와 sector_master에 씨드한다. 차트 엔드포인트
 * FID_INPUT_ISCD는 4자리 KIS 업종분류코드(0001 코스피, 1001 코스닥, 2001 코스피 200,
 * 0013 전기전자 등)만 인식하므로 이 시드의 코드를 그대로 전달한다.
 *
 * 차트 캐시: sector_index_candle, 일봉만 [CHART_CACHE_TTL_MS] (6시간) TTL.
 * 레이트 리밋: [KisApiClient]가 500ms 글로벌 + 3실패 5분 서킷 브레이커 수행.
 */
@Singleton
class SectorIndexRepository @Inject constructor(
    private val sectorMasterDao: SectorMasterDao,
    private val candleDao: SectorIndexCandleDao,
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
     * sector_master에 KIS 업종분류코드 시드가 없거나 수가 다르면 재씨드한다.
     * 네트워크 호출 없음 — 로컬 상수 테이블만 참조하므로 즉시 반환.
     *
     * @return 씨드 총 개수
     */
    suspend fun ensureSeeded(force: Boolean = false): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val existing = sectorMasterDao.count()
            val target = KisSectorCodeSeed.ENTRIES.size
            if (!force && existing == target) {
                return@withContext Result.success(existing)
            }
            val now = System.currentTimeMillis()
            sectorMasterDao.replaceAll(KisSectorCodeSeed.toEntities(now))
            Timber.i("KIS 업종분류코드 시드 적용: %d건", target)
            Result.success(target)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "업종 시드 적용 실패")
            Result.failure(e)
        }
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
                        quote = null,
                        candles = cached.map { it.toDomain() },
                    )
                )
            }
        }

        val queryParams = mapOf(
            "FID_COND_MRKT_DIV_CODE" to "U",
            "FID_INPUT_ISCD" to code,
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

    private fun SectorMasterEntity.toDomain(): SectorIndex =
        SectorIndex(code, name, SectorLevel.fromCode(level), parentCode)

    private fun SectorIndexCandleEntity.toDomain(): SectorIndexCandle =
        SectorIndexCandle(date, open, high, low, close, volume)

    companion object {
        // FHKUP03500100 = 국내주식업종기간별시세(일/주/월/년). KIS 공식 샘플과 일치.
        // 과거 FHPUP02140000로 호출 시 응답 `opsq2001 input field not found` 발생.
        private const val TR_SECTOR_INDEX_CHART = "FHKUP03500100"
        private const val EP_SECTOR_INDEX_CHART = "/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice"

        private const val CHART_CACHE_TTL_MS = 6L * 60 * 60 * 1000 // 6시간

        private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
