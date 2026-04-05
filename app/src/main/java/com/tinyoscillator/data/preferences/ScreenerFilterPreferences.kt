package com.tinyoscillator.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tinyoscillator.domain.model.MarketType
import com.tinyoscillator.domain.model.ScreenerFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.screenerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "screener_filter_preferences"
)

/**
 * 스크리너 필터 조건 DataStore 저장/복원.
 */
@Singleton
class ScreenerFilterPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore get() = context.screenerDataStore

    private object Keys {
        val MIN_SIGNAL = floatPreferencesKey("scr_min_signal")
        val MAX_SIGNAL = floatPreferencesKey("scr_max_signal")
        val MIN_MKCAP = longPreferencesKey("scr_min_mkcap")
        val MAX_MKCAP = longPreferencesKey("scr_max_mkcap")
        val MIN_PBR = floatPreferencesKey("scr_min_pbr")
        val MAX_PBR = floatPreferencesKey("scr_max_pbr")
        val MIN_FOREIGN = floatPreferencesKey("scr_min_foreign")
        val MAX_FOREIGN = floatPreferencesKey("scr_max_foreign")
        val MIN_VOLUME = floatPreferencesKey("scr_min_volume")
        val MARKET_TYPE = stringPreferencesKey("scr_market_type")
        val SECTOR_CODE = stringPreferencesKey("scr_sector_code")
    }

    val filter: Flow<ScreenerFilter> = dataStore.data.map { p ->
        ScreenerFilter(
            minSignalScore = p[Keys.MIN_SIGNAL] ?: 0.60f,
            maxSignalScore = p[Keys.MAX_SIGNAL] ?: 1.00f,
            minMarketCapBil = p[Keys.MIN_MKCAP] ?: 0L,
            maxMarketCapBil = p[Keys.MAX_MKCAP] ?: Long.MAX_VALUE,
            minPbr = p[Keys.MIN_PBR] ?: 0f,
            maxPbr = p[Keys.MAX_PBR] ?: Float.MAX_VALUE,
            minForeignRatio = p[Keys.MIN_FOREIGN] ?: 0f,
            maxForeignRatio = p[Keys.MAX_FOREIGN] ?: 1f,
            minVolumeRatio = p[Keys.MIN_VOLUME] ?: 0f,
            marketType = p[Keys.MARKET_TYPE]?.let { MarketType.valueOf(it) },
            sectorCode = p[Keys.SECTOR_CODE],
        )
    }

    suspend fun save(filter: ScreenerFilter) {
        dataStore.edit { p ->
            p[Keys.MIN_SIGNAL] = filter.minSignalScore
            p[Keys.MAX_SIGNAL] = filter.maxSignalScore
            p[Keys.MIN_MKCAP] = filter.minMarketCapBil
            p[Keys.MAX_MKCAP] = filter.maxMarketCapBil
            p[Keys.MIN_PBR] = filter.minPbr
            p[Keys.MAX_PBR] = filter.maxPbr
            p[Keys.MIN_FOREIGN] = filter.minForeignRatio
            p[Keys.MAX_FOREIGN] = filter.maxForeignRatio
            p[Keys.MIN_VOLUME] = filter.minVolumeRatio
            if (filter.marketType != null) p[Keys.MARKET_TYPE] = filter.marketType.name
            else p.remove(Keys.MARKET_TYPE)
            if (filter.sectorCode != null) p[Keys.SECTOR_CODE] = filter.sectorCode
            else p.remove(Keys.SECTOR_CODE)
        }
    }

    suspend fun reset() {
        dataStore.edit { it.clear() }
    }
}
