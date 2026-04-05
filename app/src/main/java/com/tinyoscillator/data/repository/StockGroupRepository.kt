package com.tinyoscillator.data.repository

import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.core.database.dao.UserThemeDao
import com.tinyoscillator.core.database.entity.UserThemeEntity
import com.tinyoscillator.domain.model.DEFAULT_THEMES
import com.tinyoscillator.domain.model.GroupType
import com.tinyoscillator.domain.model.StockGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockGroupRepository @Inject constructor(
    private val masterDao: StockMasterDao,
    private val themeDao: UserThemeDao,
    private val calibrationDao: CalibrationDao,
) {

    /** KRX 섹터 그룹 목록 (평균 신호 포함, avgSignal 내림차순) */
    fun observeKrxSectors(): Flow<List<StockGroup>> =
        masterDao.observeAllSectors().map { sectors ->
            val scoreMap = buildScoreMap()
            sectors.map { sectorName ->
                val tickers = masterDao.getAllTickersBySector(sectorName)
                buildGroup(
                    id = sectorName.hashCode().toLong(),
                    name = sectorName,
                    type = GroupType.KRX_SECTOR,
                    tickers = tickers,
                    scoreMap = scoreMap,
                )
            }.sortedByDescending { it.avgSignal }
        }.flowOn(Dispatchers.IO)

    /** 사용자 테마 그룹 목록 */
    fun observeUserThemes(): Flow<List<StockGroup>> =
        themeDao.observeAll().map { themes ->
            val scoreMap = buildScoreMap()
            themes.map { theme ->
                val tickers = parseTickerList(theme.tickers)
                buildGroup(
                    id = theme.id,
                    name = theme.name,
                    type = GroupType.USER_THEME,
                    tickers = tickers,
                    scoreMap = scoreMap,
                )
            }
        }.flowOn(Dispatchers.IO)

    suspend fun addTheme(name: String, tickers: List<String>) = withContext(Dispatchers.IO) {
        themeDao.insert(
            UserThemeEntity(
                name = name,
                tickers = Json.encodeToString(tickers),
                sortOrder = themeDao.getMaxSortOrder() + 1,
            )
        )
    }

    suspend fun deleteTheme(id: Long) = withContext(Dispatchers.IO) {
        themeDao.getById(id)?.let { themeDao.delete(it) }
    }

    /** 앱 최초 설치 시 기본 테마 자동 생성 */
    suspend fun initDefaultThemes() = withContext(Dispatchers.IO) {
        if (themeDao.count() > 0) return@withContext
        DEFAULT_THEMES.forEachIndexed { i, (name, tickers) ->
            themeDao.insert(
                UserThemeEntity(
                    name = name,
                    tickers = Json.encodeToString(tickers),
                    sortOrder = i,
                )
            )
        }
    }

    private suspend fun buildScoreMap(): Map<String, Float> =
        calibrationDao.getLatestAvgScoresByTicker()
            .associate { it.ticker to it.avgScore.toFloat() }

    companion object {
        fun parseTickerList(json: String): List<String> = try {
            Json.decodeFromString<List<String>>(json)
        } catch (_: Exception) {
            emptyList()
        }

        fun buildGroup(
            id: Long,
            name: String,
            type: GroupType,
            tickers: List<String>,
            scoreMap: Map<String, Float>,
        ): StockGroup {
            val signals = tickers.mapNotNull { scoreMap[it] }
            return StockGroup(
                id = id,
                name = name,
                type = type,
                tickers = tickers,
                avgSignal = if (signals.isEmpty()) 0.5f else signals.average().toFloat(),
                topSignalTicker = tickers.maxByOrNull { scoreMap[it] ?: 0f } ?: "",
                memberCount = tickers.size,
            )
        }
    }
}
