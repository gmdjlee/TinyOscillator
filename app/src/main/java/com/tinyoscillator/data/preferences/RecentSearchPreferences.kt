package com.tinyoscillator.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSearchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "recent_search_preferences"
)

/**
 * 최근 검색 종목 기록 (최대 5개, DataStore 기반).
 * 직렬화: "|" 구분자로 티커 리스트 저장.
 */
@Singleton
class RecentSearchPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore get() = context.recentSearchDataStore
    private val key = stringPreferencesKey("recent_tickers")

    val recentTickers: Flow<List<String>> = dataStore.data
        .map { prefs ->
            prefs[key]?.split("|")?.filter { it.isNotBlank() }?.take(MAX) ?: emptyList()
        }

    suspend fun addRecent(ticker: String) {
        dataStore.edit { prefs ->
            val current = prefs[key]?.split("|")?.filter { it.isNotBlank() }?.toMutableList()
                ?: mutableListOf()
            current.remove(ticker)
            current.add(0, ticker)
            prefs[key] = current.take(MAX).joinToString("|")
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.remove(key) }
    }

    companion object {
        private const val MAX = 5
    }
}
