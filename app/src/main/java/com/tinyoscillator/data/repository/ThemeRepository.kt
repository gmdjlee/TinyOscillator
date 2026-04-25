package com.tinyoscillator.data.repository

import com.tinyoscillator.BuildConfig
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.api.PageHeaders
import com.tinyoscillator.core.database.dao.ThemeGroupDao
import com.tinyoscillator.core.database.dao.ThemeStockDao
import com.tinyoscillator.core.database.entity.ThemeGroupEntity
import com.tinyoscillator.core.database.entity.ThemeStockEntity
import com.tinyoscillator.data.dto.KiwoomThemeGroupItem
import com.tinyoscillator.data.dto.KiwoomThemeListResponse
import com.tinyoscillator.data.dto.KiwoomThemeStockItem
import com.tinyoscillator.data.dto.KiwoomThemeStockResponse
import com.tinyoscillator.data.dto.ThemeApiEndpoints
import com.tinyoscillator.data.dto.ThemeApiIds
import com.tinyoscillator.data.dto.toDoubleOrZero
import com.tinyoscillator.data.dto.toIntOrZero
import com.tinyoscillator.data.dto.toLongOrZero
import com.tinyoscillator.domain.model.ThemeDataProgress
import com.tinyoscillator.domain.model.ThemeExchange
import com.tinyoscillator.domain.model.ThemeGroup
import com.tinyoscillator.domain.model.ThemeSortMode
import com.tinyoscillator.domain.model.ThemeStock
import com.tinyoscillator.domain.model.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Kiwoom REST API의 ka90001(테마그룹별요청) / ka90002(테마구성종목요청)을 캐시·동기화한다.
 *
 * **갱신 흐름** ([updateAll]):
 *  1. ka90001 페이지네이션 (`cont-yn=Y` 동안 반복, [MAX_PAGES] 안전장치)
 *  2. 첫 페이지 응답을 Timber.d로 raw 덤프 → Step 8 logcat에서 실제 응답 키 확인용
 *  3. [ThemeGroupDao.replaceAll] 단일 트랜잭션으로 전체 테마 목록 교체
 *  4. 각 테마마다 ka90002 페이지네이션 → [ThemeStockDao.replaceForTheme] 부분 교체
 *  5. 테마 단위 try/catch — 일부 ka90002 실패가 전체 갱신을 무효화하지 않음
 *
 * **조회 흐름**: [observeThemes]/[observeThemeStocks]는 Room Flow를 도메인 모델로 매핑한다.
 * 정렬 모드(`ThemeSortMode`)는 in-memory로 적용 — DAO에서 정렬을 분기하면 쿼리 다중화가
 * 폭증하기 때문이다.
 */
@Singleton
class ThemeRepository @Inject constructor(
    private val themeGroupDao: ThemeGroupDao,
    private val themeStockDao: ThemeStockDao,
    private val kiwoomApiClient: KiwoomApiClient,
    private val json: Json,
) {

    fun observeThemes(): Flow<List<ThemeGroup>> =
        themeGroupDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeThemes(query: String, sort: ThemeSortMode): Flow<List<ThemeGroup>> {
        val source = if (query.isBlank()) {
            themeGroupDao.observeAll()
        } else {
            themeGroupDao.searchByName(query.trim())
        }
        return source.map { entities ->
            val themes = entities.map { it.toDomain() }
            when (sort) {
                ThemeSortMode.TOP_RETURN -> themes.sortedByDescending { it.periodReturnRate }
                ThemeSortMode.FLU_RATE -> themes.sortedByDescending { it.fluRate }
                ThemeSortMode.NAME -> themes.sortedBy { it.themeName }
                ThemeSortMode.STOCK_COUNT -> themes.sortedByDescending { it.stockCount }
            }
        }
    }

    fun observeThemeStocks(themeCode: String): Flow<List<ThemeStock>> =
        themeStockDao.observeByTheme(themeCode).map { list -> list.map { it.toDomain() } }

    suspend fun lastUpdatedAt(): Long? = themeGroupDao.lastUpdatedAt()

    suspend fun themeCount(): Int = themeGroupDao.count()

    /**
     * 전체 테마 목록 + 각 테마의 구성 종목을 갱신한다.
     *
     * 한 번의 호출로 ka90001 N페이지 + ka90002 (테마수 × 평균 페이지 수)를 호출하므로
     * 워커 호출에서만 사용해야 한다 (UI에서 직접 호출 금지).
     *
     * @param config Kiwoom 자격증명 (모의/실전 구분 포함)
     * @param exchange 거래소 필터 (`stex_tp` 파라미터)
     * @param sortMode ka90001 호출 시 서버측 정렬 힌트 (`flu_pl_amt_tp`). UI 정렬과 별개.
     * @param dateTp 기간 구분 ("1"~"99" 일 단위, 기본 "30"일)
     */
    fun updateAll(
        config: KiwoomApiKeyConfig,
        exchange: ThemeExchange,
        sortMode: ThemeSortMode = ThemeSortMode.TOP_RETURN,
        dateTp: String = "30",
    ): Flow<ThemeDataProgress> = flow {
        if (!config.isValid()) {
            emit(ThemeDataProgress.Error("키움 API 키가 설정되지 않았습니다. 설정에서 등록해 주세요."))
            return@flow
        }

        try {
            emit(ThemeDataProgress.Loading("테마 목록 조회 중...", 0f))

            val themeItems = fetchAllThemes(config, exchange, sortMode, dateTp).getOrElse { e ->
                emit(ThemeDataProgress.Error("테마 목록 조회 실패: ${e.message ?: e.javaClass.simpleName}"))
                return@flow
            }

            if (themeItems.isEmpty()) {
                emit(ThemeDataProgress.Error("테마 목록이 비어 있습니다."))
                return@flow
            }

            val now = System.currentTimeMillis()
            val groupEntities = themeItems.mapNotNull { it.toEntityOrNull(now) }
            themeGroupDao.replaceAll(groupEntities)
            Timber.i("ka90001 캐시 갱신: %d건", groupEntities.size)

            // 각 테마의 ka90002 호출 — 일부 실패는 격리.
            var totalStocks = 0
            var firstThemeLogged = false
            val total = groupEntities.size
            groupEntities.forEachIndexed { i, group ->
                val progress = 0.05f + 0.95f * (i + 1).toFloat() / total.toFloat()
                emit(
                    ThemeDataProgress.Loading(
                        "${group.themeName} 종목 수집 중... (${i + 1}/$total)",
                        progress.coerceIn(0f, 1f),
                    )
                )
                try {
                    val logFirst = !firstThemeLogged
                    val stocks = fetchThemeStocks(
                        config = config,
                        exchange = exchange,
                        themeCode = group.themeCode,
                        dateTp = dateTp,
                        logRawFirstPage = logFirst,
                    ).getOrThrow()
                    if (logFirst) firstThemeLogged = true

                    val stockEntities = stocks.mapNotNull { it.toEntityOrNull(group.themeCode, now) }
                    themeStockDao.replaceForTheme(group.themeCode, stockEntities)
                    totalStocks += stockEntities.size
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 한 테마의 ka90002 실패가 전체 갱신을 망가뜨리지 않도록 격리.
                    Timber.w(
                        e,
                        "ka90002 실패 — 테마 격리 (code=%s, name=%s)",
                        group.themeCode,
                        group.themeName,
                    )
                }
            }

            emit(ThemeDataProgress.Success(themeCount = groupEntities.size, stockCount = totalStocks))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "테마 갱신 실패")
            emit(ThemeDataProgress.Error("테마 갱신 실패: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    // ============================================================================
    // ka90001 페이지네이션
    // ============================================================================

    private suspend fun fetchAllThemes(
        config: KiwoomApiKeyConfig,
        exchange: ThemeExchange,
        sortMode: ThemeSortMode,
        dateTp: String,
    ): Result<List<KiwoomThemeGroupItem>> {
        val accumulator = mutableListOf<KiwoomThemeGroupItem>()
        var contYn = ""
        var nextKey = ""
        var page = 0

        while (page < MAX_PAGES) {
            val isFirstPage = page == 0
            val extraHeaders = if (isFirstPage) {
                emptyMap()
            } else {
                mapOf("cont-yn" to contYn, "next-key" to nextKey)
            }

            val body = mapOf(
                "qry_tp" to "0",
                "stk_cd" to "",
                "date_tp" to dateTp,
                "thema_nm" to "",
                "flu_pl_amt_tp" to sortMode.toFluPlAmtTp(),
                "stex_tp" to exchange.code,
            )

            val callResult = kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeListResponse, PageHeaders>>(
                apiId = ThemeApiIds.THEME_GROUP_LIST,
                url = ThemeApiEndpoints.THEME_BASE,
                body = body,
                config = config,
                extraHeaders = extraHeaders,
            ) { responseBody, headers ->
                if (isFirstPage && BuildConfig.DEBUG) {
                    Timber.d(
                        "[ka90001 raw][page=1][bytes=%d] %s",
                        responseBody.length,
                        responseBody.take(2000),
                    )
                }
                json.decodeFromString<KiwoomThemeListResponse>(responseBody) to headers
            }

            val (parsed, headers) = callResult.getOrElse { return Result.failure(it) }
            parsed.themeGroups?.let { accumulator += it }

            page++
            if (headers.contYn != "Y") break
            contYn = headers.contYn
            nextKey = headers.nextKey
            if (nextKey.isBlank()) break
        }

        if (page == MAX_PAGES) {
            Timber.w("ka90001 MAX_PAGES(%d) 도달 — 잠재 누락 가능", MAX_PAGES)
        }

        return Result.success(accumulator)
    }

    // ============================================================================
    // ka90002 페이지네이션 (테마 단위)
    // ============================================================================

    private suspend fun fetchThemeStocks(
        config: KiwoomApiKeyConfig,
        exchange: ThemeExchange,
        themeCode: String,
        dateTp: String,
        logRawFirstPage: Boolean,
    ): Result<List<KiwoomThemeStockItem>> {
        val accumulator = mutableListOf<KiwoomThemeStockItem>()
        var contYn = ""
        var nextKey = ""
        var page = 0

        while (page < MAX_PAGES) {
            val isFirstPage = page == 0
            val extraHeaders = if (isFirstPage) {
                emptyMap()
            } else {
                mapOf("cont-yn" to contYn, "next-key" to nextKey)
            }

            val body = mapOf(
                "date_tp" to dateTp,
                "thema_grp_cd" to themeCode,
                "stex_tp" to exchange.code,
            )

            val callResult = kiwoomApiClient.callWithHeaders<Pair<KiwoomThemeStockResponse, PageHeaders>>(
                apiId = ThemeApiIds.THEME_COMPONENT_STOCKS,
                url = ThemeApiEndpoints.THEME_BASE,
                body = body,
                config = config,
                extraHeaders = extraHeaders,
            ) { responseBody, headers ->
                if (isFirstPage && logRawFirstPage && BuildConfig.DEBUG) {
                    Timber.d(
                        "[ka90002 raw][theme=%s][bytes=%d] %s",
                        themeCode,
                        responseBody.length,
                        responseBody.take(2000),
                    )
                }
                json.decodeFromString<KiwoomThemeStockResponse>(responseBody) to headers
            }

            val (parsed, headers) = callResult.getOrElse { return Result.failure(it) }
            parsed.themeStocks?.let { accumulator += it }

            page++
            if (headers.contYn != "Y") break
            contYn = headers.contYn
            nextKey = headers.nextKey
            if (nextKey.isBlank()) break
        }

        if (page == MAX_PAGES) {
            Timber.w("ka90002 MAX_PAGES(%d) 도달 (theme=%s)", MAX_PAGES, themeCode)
        }

        return Result.success(accumulator)
    }

    // ============================================================================
    // DTO → Entity 매퍼
    //   themeCode/stockCode 누락 행은 정상 캐시할 수 없으므로 건너뛴다.
    // ============================================================================

    private fun KiwoomThemeGroupItem.toEntityOrNull(lastUpdated: Long): ThemeGroupEntity? {
        val code = themeCode?.takeIf { it.isNotBlank() } ?: return null
        return ThemeGroupEntity(
            themeCode = code,
            themeName = themeName?.takeIf { it.isNotBlank() } ?: code,
            stockCount = stockCount.toIntOrZero(),
            fluRate = fluctuationRate.toDoubleOrZero(),
            periodReturnRate = periodReturnRate.toDoubleOrZero(),
            riseCount = risingStockCount.toIntOrZero(),
            fallCount = fallingStockCount.toIntOrZero(),
            mainStocks = mainStocks.orEmpty(),
            lastUpdated = lastUpdated,
        )
    }

    private fun KiwoomThemeStockItem.toEntityOrNull(themeCode: String, lastUpdated: Long): ThemeStockEntity? {
        val code = stockCode?.takeIf { it.isNotBlank() } ?: return null
        return ThemeStockEntity(
            themeCode = themeCode,
            stockCode = code,
            stockName = stockName?.takeIf { it.isNotBlank() } ?: code,
            currentPrice = currentPrice.toDoubleOrZero(),
            priorDiff = priorDiff.toDoubleOrZero(),
            fluRate = fluctuationRate.toDoubleOrZero(),
            volume = accumulatedVolume.toLongOrZero(),
            periodReturnRate = periodReturnRate.toDoubleOrZero(),
            lastUpdated = lastUpdated,
        )
    }

    private fun ThemeSortMode.toFluPlAmtTp(): String = when (this) {
        ThemeSortMode.TOP_RETURN -> "1"   // 상위기간수익률
        ThemeSortMode.FLU_RATE -> "3"     // 상위등락률
        ThemeSortMode.NAME -> "1"         // 정렬 무관 (UI에서 in-memory 정렬)
        ThemeSortMode.STOCK_COUNT -> "1"  // 정렬 무관
    }

    companion object {
        /** 무한 루프 방지 안전장치. 200~400개 테마라도 페이지당 ~50건이면 충분. */
        private const val MAX_PAGES = 50
    }
}
