package com.tinyoscillator.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tinyoscillator.domain.model.Indicator
import com.tinyoscillator.domain.model.IndicatorParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class IndicatorParamsDto(
    val period: Int = 14,
    val fast: Int = 12,
    val slow: Int = 26,
    val signal: Int = 9,
    val multiplier: Float = 2f,
) {
    fun toParams() = IndicatorParams(period, fast, slow, signal, multiplier)

    companion object {
        fun from(p: IndicatorParams) = IndicatorParamsDto(p.period, p.fast, p.slow, p.signal, p.multiplier)
    }
}

@Singleton
class IndicatorPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        val SELECTED_KEY = stringPreferencesKey("selected_indicators")
        const val PARAMS_PREFIX = "indicator_params_"
        val DEFAULTS = setOf(Indicator.EMA_SHORT, Indicator.EMA_MID)
    }

    val selectedIndicators: Flow<Set<Indicator>> = dataStore.data.map { prefs ->
        val raw = prefs[SELECTED_KEY] ?: return@map DEFAULTS
        try {
            json.decodeFromString<List<String>>(raw)
                .mapNotNull { name -> Indicator.entries.firstOrNull { it.name == name } }
                .toSet()
        } catch (e: Exception) {
            Timber.w(e, "선택 지표 JSON 파싱 실패, 기본값으로 복원 (raw=%s)", raw)
            DEFAULTS
        }
    }

    val indicatorParams: Flow<Map<Indicator, IndicatorParams>> = dataStore.data.map { prefs ->
        val result = mutableMapOf<Indicator, IndicatorParams>()
        Indicator.entries.forEach { ind ->
            val key = stringPreferencesKey("$PARAMS_PREFIX${ind.name}")
            prefs[key]?.let { raw ->
                try {
                    result[ind] = json.decodeFromString<IndicatorParamsDto>(raw).toParams()
                } catch (e: Exception) {
                    Timber.w(e, "지표 파라미터 파싱 실패, 해당 지표는 기본값 사용 (%s, raw=%s)", ind.name, raw)
                }
            }
        }
        result
    }

    suspend fun toggle(indicator: Indicator) {
        dataStore.edit { prefs ->
            val current = prefs[SELECTED_KEY]?.let { raw ->
                try {
                    json.decodeFromString<List<String>>(raw).toMutableSet()
                } catch (e: Exception) {
                    Timber.w(e, "토글용 선택 지표 JSON 파싱 실패, 기본값으로 복원 (raw=%s)", raw)
                    DEFAULTS.map { it.name }.toMutableSet()
                }
            } ?: DEFAULTS.map { it.name }.toMutableSet()

            if (indicator.name in current) {
                current.remove(indicator.name)
            } else {
                current.add(indicator.name)
            }
            prefs[SELECTED_KEY] = json.encodeToString(current.toList())
        }
    }

    suspend fun updateParams(indicator: Indicator, params: IndicatorParams) {
        dataStore.edit { prefs ->
            val key = stringPreferencesKey("$PARAMS_PREFIX${indicator.name}")
            prefs[key] = json.encodeToString(IndicatorParamsDto.from(params))
        }
    }
}
