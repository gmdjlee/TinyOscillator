package com.tinyoscillator.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object PrefsKeys {
    const val KIWOOM_APP_KEY = "kiwoom_app_key"
    const val KIWOOM_SECRET_KEY = "kiwoom_secret_key"
    const val KIWOOM_MODE = "kiwoom_mode"
    const val KIS_APP_KEY = "kis_app_key"
    const val KIS_APP_SECRET = "kis_app_secret"
    const val KIS_MODE = "kis_mode"
    const val KRX_ID = "krx_id"
    const val KRX_PASSWORD = "krx_password"
    const val AI_API_KEY = "ai_api_key"
    const val AI_PROVIDER = "ai_provider"
    const val AI_MODEL_ID = "ai_model_id"
    const val ETF_INCLUDE_KEYWORDS = "etf_include_keywords"
    const val ETF_EXCLUDE_KEYWORDS = "etf_exclude_keywords"
    const val ETF_SCHEDULE_HOUR = "etf_schedule_hour"
    const val ETF_SCHEDULE_MINUTE = "etf_schedule_minute"
    const val ETF_SCHEDULE_ENABLED = "etf_schedule_enabled"
    const val OSCILLATOR_SCHEDULE_HOUR = "oscillator_schedule_hour"
    const val OSCILLATOR_SCHEDULE_MINUTE = "oscillator_schedule_minute"
    const val OSCILLATOR_SCHEDULE_ENABLED = "oscillator_schedule_enabled"
    const val ETF_COLLECTION_DAYS = "etf_collection_days"
    const val MARKET_OSCILLATOR_COLLECTION_DAYS = "market_oscillator_collection_days"
    const val MARKET_DEPOSIT_COLLECTION_DAYS = "market_deposit_collection_days"
    const val DEPOSIT_SCHEDULE_HOUR = "deposit_schedule_hour"
    const val DEPOSIT_SCHEDULE_MINUTE = "deposit_schedule_minute"
    const val DEPOSIT_SCHEDULE_ENABLED = "deposit_schedule_enabled"
    const val MARKET_CLOSE_REFRESH_HOUR = "market_close_refresh_hour"
    const val MARKET_CLOSE_REFRESH_MINUTE = "market_close_refresh_minute"
    const val MARKET_CLOSE_REFRESH_ENABLED = "market_close_refresh_enabled"
    const val CONSENSUS_SCHEDULE_HOUR = "consensus_schedule_hour"
    const val CONSENSUS_SCHEDULE_MINUTE = "consensus_schedule_minute"
    const val CONSENSUS_SCHEDULE_ENABLED = "consensus_schedule_enabled"
    const val CONSENSUS_COLLECTION_DAYS = "consensus_collection_days"
    const val FEAR_GREED_COLLECTION_DAYS = "fear_greed_collection_days"
    const val FEAR_GREED_SCHEDULE_HOUR = "fear_greed_schedule_hour"
    const val FEAR_GREED_SCHEDULE_MINUTE = "fear_greed_schedule_minute"
    const val FEAR_GREED_SCHEDULE_ENABLED = "fear_greed_schedule_enabled"
    const val DART_API_KEY = "dart_api_key"
    const val ECOS_API_KEY = "ecos_api_key"
    const val THEME_SCHEDULE_HOUR = "theme_schedule_hour"
    const val THEME_SCHEDULE_MINUTE = "theme_schedule_minute"
    const val THEME_SCHEDULE_ENABLED = "theme_schedule_enabled"
    const val THEME_EXCHANGE = "theme_exchange"
}

// KrxCredentials and EtfKeywordFilter moved to domain.model.EtfModels.kt
// Re-export for backward compatibility
typealias KrxCredentials = com.tinyoscillator.domain.model.KrxCredentials
typealias EtfKeywordFilter = com.tinyoscillator.domain.model.EtfKeywordFilter

data class EtfScheduleTime(val hour: Int = 0, val minute: Int = 30, val enabled: Boolean = true)

data class OscillatorScheduleTime(val hour: Int = 1, val minute: Int = 0, val enabled: Boolean = false)

data class DepositScheduleTime(val hour: Int = 2, val minute: Int = 0, val enabled: Boolean = false)

data class MarketCloseRefreshScheduleTime(val hour: Int = 19, val minute: Int = 0, val enabled: Boolean = false)

data class ConsensusScheduleTime(val hour: Int = 3, val minute: Int = 0, val enabled: Boolean = false)

data class EtfCollectionPeriod(val daysBack: Int = 14)

data class MarketOscillatorCollectionPeriod(val daysBack: Int = 30)
data class MarketDepositCollectionPeriod(val daysBack: Int = 365)
data class ConsensusCollectionPeriod(val daysBack: Int = 30)

data class FearGreedCollectionPeriod(val daysBack: Int = 365)
data class FearGreedScheduleTime(val hour: Int = 4, val minute: Int = 0, val enabled: Boolean = false)

data class ThemeScheduleTime(val hour: Int = 2, val minute: Int = 30, val enabled: Boolean = false)

internal val DEFAULT_INCLUDE_KEYWORDS = listOf(
    "반도체", "바이오", "혁신기술", "배당성장", "신재생",
    "2차전지", "AI", "조선", "테크", "수출", "로봇",
    "컬처", "밸류업", "친환경", "소비", "이노베이션",
    "메모리", "비메모리", "인공지능", "전기차", "배터리",
    "ESG", "탄소중립", "메타버스", "블록체인", "헬스케어",
    "IT", "성장"
)
internal val DEFAULT_EXCLUDE_KEYWORDS = listOf(
    "인버스", "레버리지", "2X", "곱버스", "합성", "3X",
    "글로벌", "차이나", "채권", "달러", "China",
    "아시아", "미국", "일본", "금리", "금융채", "회사채"
)

@Volatile
private var cachedEncryptedPrefs: SharedPreferences? = null
private val prefsLock = Any()

internal fun getEncryptedPrefs(context: Context): SharedPreferences {
    cachedEncryptedPrefs?.let { return it }
    synchronized(prefsLock) {
        cachedEncryptedPrefs?.let { return it }
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context.applicationContext,
            "api_settings_encrypted",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ).also { cachedEncryptedPrefs = it }
    }
}

// ───────────────────────────── KRX credentials ─────────────────────────────

suspend fun loadKrxCredentials(context: Context): KrxCredentials = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    KrxCredentials(
        id = prefs.getString(PrefsKeys.KRX_ID, "") ?: "",
        password = prefs.getString(PrefsKeys.KRX_PASSWORD, "") ?: ""
    )
}

suspend fun saveKrxCredentials(context: Context, creds: KrxCredentials) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putString(PrefsKeys.KRX_ID, creds.id)
        .putString(PrefsKeys.KRX_PASSWORD, creds.password)
        .apply()
}

// ─────────────────────────── ETF keyword filter ───────────────────────────

suspend fun loadEtfKeywordFilter(context: Context): EtfKeywordFilter = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    val includeStr = prefs.getString(PrefsKeys.ETF_INCLUDE_KEYWORDS, null)
    val excludeStr = prefs.getString(PrefsKeys.ETF_EXCLUDE_KEYWORDS, null)
    EtfKeywordFilter(
        includeKeywords = includeStr?.split(",")?.filter { it.isNotBlank() } ?: DEFAULT_INCLUDE_KEYWORDS,
        excludeKeywords = excludeStr?.split(",")?.filter { it.isNotBlank() } ?: DEFAULT_EXCLUDE_KEYWORDS
    )
}

suspend fun saveEtfKeywordFilter(context: Context, filter: EtfKeywordFilter) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putString(PrefsKeys.ETF_INCLUDE_KEYWORDS, filter.includeKeywords.joinToString(","))
        .putString(PrefsKeys.ETF_EXCLUDE_KEYWORDS, filter.excludeKeywords.joinToString(","))
        .apply()
}

// ──────────────────────────── Schedule times ────────────────────────────

suspend fun loadEtfScheduleTime(context: Context): EtfScheduleTime = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    EtfScheduleTime(
        hour = prefs.getInt(PrefsKeys.ETF_SCHEDULE_HOUR, 0),
        minute = prefs.getInt(PrefsKeys.ETF_SCHEDULE_MINUTE, 30),
        enabled = prefs.getBoolean(PrefsKeys.ETF_SCHEDULE_ENABLED, true)
    )
}

suspend fun saveEtfScheduleTime(context: Context, schedule: EtfScheduleTime) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.ETF_SCHEDULE_HOUR, schedule.hour)
        .putInt(PrefsKeys.ETF_SCHEDULE_MINUTE, schedule.minute)
        .putBoolean(PrefsKeys.ETF_SCHEDULE_ENABLED, schedule.enabled)
        .apply()
}

suspend fun loadOscillatorScheduleTime(context: Context): OscillatorScheduleTime = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    OscillatorScheduleTime(
        hour = prefs.getInt(PrefsKeys.OSCILLATOR_SCHEDULE_HOUR, 1),
        minute = prefs.getInt(PrefsKeys.OSCILLATOR_SCHEDULE_MINUTE, 0),
        enabled = prefs.getBoolean(PrefsKeys.OSCILLATOR_SCHEDULE_ENABLED, false)
    )
}

suspend fun saveOscillatorScheduleTime(context: Context, schedule: OscillatorScheduleTime) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.OSCILLATOR_SCHEDULE_HOUR, schedule.hour)
        .putInt(PrefsKeys.OSCILLATOR_SCHEDULE_MINUTE, schedule.minute)
        .putBoolean(PrefsKeys.OSCILLATOR_SCHEDULE_ENABLED, schedule.enabled)
        .apply()
}

suspend fun loadDepositScheduleTime(context: Context): DepositScheduleTime = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    DepositScheduleTime(
        hour = prefs.getInt(PrefsKeys.DEPOSIT_SCHEDULE_HOUR, 2),
        minute = prefs.getInt(PrefsKeys.DEPOSIT_SCHEDULE_MINUTE, 0),
        enabled = prefs.getBoolean(PrefsKeys.DEPOSIT_SCHEDULE_ENABLED, false)
    )
}

suspend fun saveDepositScheduleTime(context: Context, schedule: DepositScheduleTime) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.DEPOSIT_SCHEDULE_HOUR, schedule.hour)
        .putInt(PrefsKeys.DEPOSIT_SCHEDULE_MINUTE, schedule.minute)
        .putBoolean(PrefsKeys.DEPOSIT_SCHEDULE_ENABLED, schedule.enabled)
        .apply()
}

suspend fun loadMarketCloseRefreshScheduleTime(context: Context): MarketCloseRefreshScheduleTime = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    MarketCloseRefreshScheduleTime(
        hour = prefs.getInt(PrefsKeys.MARKET_CLOSE_REFRESH_HOUR, 19),
        minute = prefs.getInt(PrefsKeys.MARKET_CLOSE_REFRESH_MINUTE, 0),
        enabled = prefs.getBoolean(PrefsKeys.MARKET_CLOSE_REFRESH_ENABLED, false)
    )
}

suspend fun saveMarketCloseRefreshScheduleTime(context: Context, schedule: MarketCloseRefreshScheduleTime) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.MARKET_CLOSE_REFRESH_HOUR, schedule.hour)
        .putInt(PrefsKeys.MARKET_CLOSE_REFRESH_MINUTE, schedule.minute)
        .putBoolean(PrefsKeys.MARKET_CLOSE_REFRESH_ENABLED, schedule.enabled)
        .apply()
}

suspend fun loadConsensusScheduleTime(context: Context): ConsensusScheduleTime = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    ConsensusScheduleTime(
        hour = prefs.getInt(PrefsKeys.CONSENSUS_SCHEDULE_HOUR, 3),
        minute = prefs.getInt(PrefsKeys.CONSENSUS_SCHEDULE_MINUTE, 0),
        enabled = prefs.getBoolean(PrefsKeys.CONSENSUS_SCHEDULE_ENABLED, false)
    )
}

suspend fun saveConsensusScheduleTime(context: Context, schedule: ConsensusScheduleTime) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.CONSENSUS_SCHEDULE_HOUR, schedule.hour)
        .putInt(PrefsKeys.CONSENSUS_SCHEDULE_MINUTE, schedule.minute)
        .putBoolean(PrefsKeys.CONSENSUS_SCHEDULE_ENABLED, schedule.enabled)
        .apply()
}

suspend fun loadFearGreedScheduleTime(context: Context): FearGreedScheduleTime = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    FearGreedScheduleTime(
        hour = prefs.getInt(PrefsKeys.FEAR_GREED_SCHEDULE_HOUR, 4),
        minute = prefs.getInt(PrefsKeys.FEAR_GREED_SCHEDULE_MINUTE, 0),
        enabled = prefs.getBoolean(PrefsKeys.FEAR_GREED_SCHEDULE_ENABLED, false)
    )
}

suspend fun saveFearGreedScheduleTime(context: Context, schedule: FearGreedScheduleTime) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.FEAR_GREED_SCHEDULE_HOUR, schedule.hour)
        .putInt(PrefsKeys.FEAR_GREED_SCHEDULE_MINUTE, schedule.minute)
        .putBoolean(PrefsKeys.FEAR_GREED_SCHEDULE_ENABLED, schedule.enabled)
        .apply()
}

suspend fun loadThemeScheduleTime(context: Context): ThemeScheduleTime = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    ThemeScheduleTime(
        hour = prefs.getInt(PrefsKeys.THEME_SCHEDULE_HOUR, 2),
        minute = prefs.getInt(PrefsKeys.THEME_SCHEDULE_MINUTE, 30),
        enabled = prefs.getBoolean(PrefsKeys.THEME_SCHEDULE_ENABLED, false)
    )
}

suspend fun saveThemeScheduleTime(context: Context, schedule: ThemeScheduleTime) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.THEME_SCHEDULE_HOUR, schedule.hour)
        .putInt(PrefsKeys.THEME_SCHEDULE_MINUTE, schedule.minute)
        .putBoolean(PrefsKeys.THEME_SCHEDULE_ENABLED, schedule.enabled)
        .apply()
}

suspend fun loadThemeExchangeFilter(context: Context): com.tinyoscillator.domain.model.ThemeExchange = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    com.tinyoscillator.domain.model.ThemeExchange.fromCode(
        prefs.getString(PrefsKeys.THEME_EXCHANGE, com.tinyoscillator.domain.model.ThemeExchange.KRX.code)
    )
}

suspend fun saveThemeExchangeFilter(context: Context, exchange: com.tinyoscillator.domain.model.ThemeExchange) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putString(PrefsKeys.THEME_EXCHANGE, exchange.code)
        .apply()
}

// ──────────────────────────── Collection periods ────────────────────────────

suspend fun loadEtfCollectionPeriod(context: Context): EtfCollectionPeriod = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    EtfCollectionPeriod(
        daysBack = prefs.getInt(PrefsKeys.ETF_COLLECTION_DAYS, 14)
    )
}

suspend fun saveEtfCollectionPeriod(context: Context, period: EtfCollectionPeriod) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.ETF_COLLECTION_DAYS, period.daysBack)
        .apply()
}

suspend fun loadMarketOscillatorCollectionPeriod(context: Context): MarketOscillatorCollectionPeriod = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    MarketOscillatorCollectionPeriod(
        daysBack = prefs.getInt(PrefsKeys.MARKET_OSCILLATOR_COLLECTION_DAYS, 30)
    )
}

suspend fun saveMarketOscillatorCollectionPeriod(context: Context, period: MarketOscillatorCollectionPeriod) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.MARKET_OSCILLATOR_COLLECTION_DAYS, period.daysBack)
        .apply()
}

suspend fun loadMarketDepositCollectionPeriod(context: Context): MarketDepositCollectionPeriod = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    MarketDepositCollectionPeriod(
        daysBack = prefs.getInt(PrefsKeys.MARKET_DEPOSIT_COLLECTION_DAYS, 365)
    )
}

suspend fun saveMarketDepositCollectionPeriod(context: Context, period: MarketDepositCollectionPeriod) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.MARKET_DEPOSIT_COLLECTION_DAYS, period.daysBack)
        .apply()
}

suspend fun loadConsensusCollectionPeriod(context: Context): ConsensusCollectionPeriod = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    ConsensusCollectionPeriod(
        daysBack = prefs.getInt(PrefsKeys.CONSENSUS_COLLECTION_DAYS, 30)
    )
}

suspend fun saveConsensusCollectionPeriod(context: Context, period: ConsensusCollectionPeriod) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.CONSENSUS_COLLECTION_DAYS, period.daysBack)
        .apply()
}

suspend fun loadFearGreedCollectionPeriod(context: Context): FearGreedCollectionPeriod = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    FearGreedCollectionPeriod(
        daysBack = prefs.getInt(PrefsKeys.FEAR_GREED_COLLECTION_DAYS, 365)
    )
}

suspend fun saveFearGreedCollectionPeriod(context: Context, period: FearGreedCollectionPeriod) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.FEAR_GREED_COLLECTION_DAYS, period.daysBack)
        .apply()
}

// ───────────────────────────── API configs ─────────────────────────────

/**
 * EncryptedSharedPreferences에서 Kiwoom API 키 설정을 로드합니다.
 */
suspend fun loadKiwoomConfig(context: Context): KiwoomApiKeyConfig = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    KiwoomApiKeyConfig(
        appKey = prefs.getString(PrefsKeys.KIWOOM_APP_KEY, "") ?: "",
        secretKey = prefs.getString(PrefsKeys.KIWOOM_SECRET_KEY, "") ?: "",
        investmentMode = InvestmentMode.entries.find {
            it.name == prefs.getString(PrefsKeys.KIWOOM_MODE, "MOCK")
        } ?: InvestmentMode.MOCK
    )
}

/**
 * EncryptedSharedPreferences에서 KIS API 키 설정을 로드합니다.
 */
suspend fun loadKisConfig(context: Context): KisApiKeyConfig = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    KisApiKeyConfig(
        appKey = prefs.getString(PrefsKeys.KIS_APP_KEY, "") ?: "",
        appSecret = prefs.getString(PrefsKeys.KIS_APP_SECRET, "") ?: "",
        investmentMode = InvestmentMode.entries.find {
            it.name == prefs.getString(PrefsKeys.KIS_MODE, "MOCK")
        } ?: InvestmentMode.MOCK
    )
}

suspend fun loadAiConfig(context: Context): AiApiKeyConfig = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    val providerName = prefs.getString(PrefsKeys.AI_PROVIDER, AiProvider.CLAUDE.name) ?: AiProvider.CLAUDE.name

    // 이전 버전 마이그레이션 (CLAUDE_HAIKU 등 → CLAUDE/GEMINI)
    val provider = when (providerName) {
        "CLAUDE_HAIKU", "CLAUDE_SONNET" -> AiProvider.CLAUDE
        "GEMINI_FLASH", "GEMINI_2_5_FLASH" -> AiProvider.GEMINI
        else -> AiProvider.entries.find { it.name == providerName } ?: AiProvider.CLAUDE
    }

    val modelId = (prefs.getString(PrefsKeys.AI_MODEL_ID, "") ?: "").ifBlank {
        // modelId 미저장 시 이전 하드코딩 값으로 폴백
        when (providerName) {
            "CLAUDE_HAIKU" -> "claude-haiku-4-5-20251001"
            "CLAUDE_SONNET" -> "claude-sonnet-4-6"
            "GEMINI_FLASH" -> "gemini-2.0-flash"
            "GEMINI_2_5_FLASH" -> "gemini-2.5-flash"
            else -> ""
        }
    }

    AiApiKeyConfig(
        provider = provider,
        apiKey = prefs.getString(PrefsKeys.AI_API_KEY, "") ?: "",
        modelId = modelId
    )
}

suspend fun saveAiConfig(context: Context, config: AiApiKeyConfig) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putString(PrefsKeys.AI_PROVIDER, config.provider.name)
        .putString(PrefsKeys.AI_API_KEY, config.apiKey)
        .putString(PrefsKeys.AI_MODEL_ID, config.modelId)
        .apply()
}

suspend fun loadDartApiKey(context: Context): String = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    prefs.getString(PrefsKeys.DART_API_KEY, "") ?: ""
}

suspend fun saveDartApiKey(context: Context, apiKey: String) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putString(PrefsKeys.DART_API_KEY, apiKey)
        .apply()
}

suspend fun loadEcosApiKey(context: Context): String = withContext(Dispatchers.IO) {
    val prefs = getEncryptedPrefs(context)
    prefs.getString(PrefsKeys.ECOS_API_KEY, "") ?: ""
}

suspend fun saveEcosApiKey(context: Context, apiKey: String) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putString(PrefsKeys.ECOS_API_KEY, apiKey)
        .apply()
}
