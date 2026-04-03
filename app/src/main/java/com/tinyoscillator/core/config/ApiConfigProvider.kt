package com.tinyoscillator.core.config

import android.content.Context
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.KrxCredentials
import com.tinyoscillator.presentation.settings.loadAiConfig
import com.tinyoscillator.presentation.settings.loadDartApiKey
import com.tinyoscillator.presentation.settings.loadKisConfig
import com.tinyoscillator.presentation.settings.loadKiwoomConfig
import com.tinyoscillator.presentation.settings.loadKrxCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized API configuration provider with thread-safe caching.
 * Replaces duplicate config loading patterns across ViewModels.
 */
@Singleton
class ApiConfigProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var cachedKiwoomConfig: KiwoomApiKeyConfig? = null
    @Volatile
    private var cachedKisConfig: KisApiKeyConfig? = null
    @Volatile
    private var cachedKrxCredentials: KrxCredentials? = null
    @Volatile
    private var cachedAiConfig: AiApiKeyConfig? = null
    @Volatile
    private var cachedDartApiKey: String? = null

    private val kiwoomMutex = Mutex()
    private val kisMutex = Mutex()
    private val krxMutex = Mutex()
    private val aiMutex = Mutex()
    private val dartMutex = Mutex()

    suspend fun getKiwoomConfig(): KiwoomApiKeyConfig {
        cachedKiwoomConfig?.let { return it }
        return kiwoomMutex.withLock {
            cachedKiwoomConfig?.let { return@withLock it }
            val config = loadKiwoomConfig(context)
            cachedKiwoomConfig = config
            config
        }
    }

    suspend fun getKisConfig(): KisApiKeyConfig {
        cachedKisConfig?.let { return it }
        return kisMutex.withLock {
            cachedKisConfig?.let { return@withLock it }
            val config = loadKisConfig(context)
            cachedKisConfig = config
            config
        }
    }

    suspend fun getKrxCredentials(): KrxCredentials {
        cachedKrxCredentials?.let { return it }
        return krxMutex.withLock {
            cachedKrxCredentials?.let { return@withLock it }
            val creds = loadKrxCredentials(context)
            cachedKrxCredentials = creds
            creds
        }
    }

    suspend fun getAiConfig(): AiApiKeyConfig {
        cachedAiConfig?.let { return it }
        return aiMutex.withLock {
            cachedAiConfig?.let { return@withLock it }
            val config = loadAiConfig(context)
            cachedAiConfig = config
            config
        }
    }

    suspend fun getDartApiKey(): String? {
        cachedDartApiKey?.let { return it.ifBlank { null } }
        return dartMutex.withLock {
            cachedDartApiKey?.let { return@withLock it.ifBlank { null } }
            val key = loadDartApiKey(context)
            cachedDartApiKey = key
            key.ifBlank { null }
        }
    }

    /** Invalidate all cached configs (call after settings changes). */
    fun invalidateAll() {
        cachedKiwoomConfig = null
        cachedKisConfig = null
        cachedKrxCredentials = null
        cachedAiConfig = null
        cachedDartApiKey = null
    }

    /** Invalidate only Kiwoom config cache. */
    fun invalidateKiwoom() {
        cachedKiwoomConfig = null
    }

    /** Invalidate only KIS config cache. */
    fun invalidateKis() {
        cachedKisConfig = null
    }
}
