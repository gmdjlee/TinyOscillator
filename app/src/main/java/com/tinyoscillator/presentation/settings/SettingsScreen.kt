package com.tinyoscillator.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tinyoscillator.presentation.common.ScrollablePillTabRow
import com.tinyoscillator.presentation.common.ThemeToggleIcon
import com.tinyoscillator.ui.theme.LocalThemeModeState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.worker.EtfUpdateWorker
import com.tinyoscillator.core.worker.KEY_MESSAGE
import com.tinyoscillator.core.worker.KEY_PROGRESS
import com.tinyoscillator.core.worker.MarketDepositUpdateWorker
import com.tinyoscillator.core.worker.MarketOscillatorUpdateWorker
import com.tinyoscillator.core.worker.ConsensusUpdateWorker
import com.tinyoscillator.core.worker.DataIntegrityCheckWorker
import com.tinyoscillator.core.worker.MarketCloseRefreshWorker
import com.tinyoscillator.core.worker.WorkManagerHelper
import com.tinyoscillator.core.database.entity.WorkerLogEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

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

private val DEFAULT_INCLUDE_KEYWORDS = listOf(
    "반도체", "바이오", "혁신기술", "배당성장", "신재생",
    "2차전지", "AI", "조선", "테크", "수출", "로봇",
    "컬처", "밸류업", "친환경", "소비", "이노베이션",
    "메모리", "비메모리", "인공지능", "전기차", "배터리",
    "ESG", "탄소중립", "메타버스", "블록체인", "헬스케어",
    "IT", "성장"
)
private val DEFAULT_EXCLUDE_KEYWORDS = listOf(
    "인버스", "레버리지", "2X", "곱버스", "합성", "3X",
    "글로벌", "차이나", "채권", "달러", "China",
    "아시아", "미국", "일본", "금리", "금융채", "회사채"
)

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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun appDatabase(): AppDatabase
    fun workerLogDao(): com.tinyoscillator.core.database.dao.WorkerLogDao
}

data class CollectionState(
    val isCollecting: Boolean = false,
    val progress: Float? = null,
    val message: String? = null
)

@Composable
private fun rememberCollectionState(workInfos: List<WorkInfo>): CollectionState {
    var lastMessage by remember { mutableStateOf<String?>(null) }
    var showLastMessage by remember { mutableStateOf(false) }

    val runningInfo = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }

    LaunchedEffect(runningInfo, workInfos) {
        if (runningInfo != null) {
            val msg = runningInfo.progress.getString(KEY_MESSAGE)
            if (!msg.isNullOrBlank()) lastMessage = msg
            showLastMessage = true
        } else {
            // Worker finished — keep last message visible for 5 seconds
            if (showLastMessage && lastMessage != null) {
                delay(5000)
                showLastMessage = false
                lastMessage = null
            }
        }
    }

    return if (runningInfo != null) {
        val progress = runningInfo.progress.getFloat(KEY_PROGRESS, 0f)
        val message = runningInfo.progress.getString(KEY_MESSAGE)
        if (!message.isNullOrBlank()) lastMessage = message
        CollectionState(
            isCollecting = true,
            progress = progress,
            message = message ?: lastMessage
        )
    } else {
        CollectionState(
            isCollecting = false,
            progress = null,
            message = if (showLastMessage) lastMessage else null
        )
    }
}

private val TAB_TITLES = listOf("API", "ETF", "수집설정", "Schedule", "로그", "Backup")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }

    var kiwoomAppKey by remember { mutableStateOf("") }
    var kiwoomSecretKey by remember { mutableStateOf("") }
    var kiwoomMode by remember { mutableStateOf(InvestmentMode.MOCK) }

    var kisAppKey by remember { mutableStateOf("") }
    var kisAppSecret by remember { mutableStateOf("") }
    var kisMode by remember { mutableStateOf(InvestmentMode.MOCK) }

    var krxId by remember { mutableStateOf("") }
    var krxPassword by remember { mutableStateOf("") }

    var aiApiKey by remember { mutableStateOf("") }
    var aiProvider by remember { mutableStateOf(AiProvider.CLAUDE) }
    var aiModelId by remember { mutableStateOf("") }

    var dartApiKey by remember { mutableStateOf("") }
    var ecosApiKey by remember { mutableStateOf("") }

    var includeKeywords by remember { mutableStateOf(DEFAULT_INCLUDE_KEYWORDS) }
    var excludeKeywords by remember { mutableStateOf(DEFAULT_EXCLUDE_KEYWORDS) }
    var etfCollectionDays by remember { mutableIntStateOf(14) }
    var showAddIncludeDialog by remember { mutableStateOf(false) }
    var showAddExcludeDialog by remember { mutableStateOf(false) }

    var etfScheduleEnabled by remember { mutableStateOf(true) }
    var scheduleHour by remember { mutableIntStateOf(0) }
    var scheduleMinute by remember { mutableIntStateOf(30) }
    var oscScheduleEnabled by remember { mutableStateOf(false) }
    var oscScheduleHour by remember { mutableIntStateOf(1) }
    var oscScheduleMinute by remember { mutableIntStateOf(0) }
    var depositScheduleEnabled by remember { mutableStateOf(false) }
    var depositScheduleHour by remember { mutableIntStateOf(2) }
    var depositScheduleMinute by remember { mutableIntStateOf(0) }

    var marketCloseRefreshEnabled by remember { mutableStateOf(false) }
    var marketCloseRefreshHour by remember { mutableIntStateOf(19) }
    var marketCloseRefreshMinute by remember { mutableIntStateOf(0) }

    var consensusScheduleEnabled by remember { mutableStateOf(false) }
    var consensusScheduleHour by remember { mutableIntStateOf(3) }
    var consensusScheduleMinute by remember { mutableIntStateOf(0) }

    var fgScheduleEnabled by remember { mutableStateOf(false) }
    var fgScheduleHour by remember { mutableIntStateOf(4) }
    var fgScheduleMinute by remember { mutableIntStateOf(0) }
    var fearGreedCollectionDays by remember { mutableIntStateOf(365) }

    var marketOscCollectionDays by remember { mutableIntStateOf(30) }
    var marketDepositCollectionDays by remember { mutableIntStateOf(365) }
    var consensusCollectionDays by remember { mutableIntStateOf(30) }
    var showResetConfirmDialog by remember { mutableStateOf<String?>(null) }

    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Worker execution logs
    var lastEtfLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
    var lastOscLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
    var lastDepositLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
    var lastMarketCloseLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
    var lastConsensusLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
    var lastFearGreedLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
    var lastIntegrityLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
    var allLogs by remember { mutableStateOf<List<WorkerLogEntity>>(emptyList()) }
    var logFilter by remember { mutableStateOf(LogFilter.ALL) }

    val entryPoint = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsEntryPoint::class.java)
    }

    // Observe WorkManager progress for each collection type
    val workManager = remember { WorkManager.getInstance(context) }
    val etfWorkInfos by workManager.getWorkInfosByTagFlow(EtfUpdateWorker.TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val oscWorkInfos by workManager.getWorkInfosByTagFlow(MarketOscillatorUpdateWorker.TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val depositWorkInfos by workManager.getWorkInfosByTagFlow(MarketDepositUpdateWorker.TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val integrityWorkInfos by workManager.getWorkInfosByTagFlow(DataIntegrityCheckWorker.TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val marketCloseRefreshWorkInfos by workManager.getWorkInfosByTagFlow(MarketCloseRefreshWorker.TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val consensusWorkInfos by workManager.getWorkInfosByTagFlow(ConsensusUpdateWorker.TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val fgWorkInfos by workManager.getWorkInfosByTagFlow(com.tinyoscillator.core.worker.FearGreedUpdateWorker.TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val etfCollectionState = rememberCollectionState(etfWorkInfos)
    val oscCollectionState = rememberCollectionState(oscWorkInfos)
    val depositCollectionState = rememberCollectionState(depositWorkInfos)
    val integrityCheckState = rememberCollectionState(integrityWorkInfos)
    val marketCloseRefreshState = rememberCollectionState(marketCloseRefreshWorkInfos)
    val consensusCollectionState = rememberCollectionState(consensusWorkInfos)
    val fgCollectionState = rememberCollectionState(fgWorkInfos)

    LaunchedEffect(Unit) {
        try {
            val kiwoomConfig = loadKiwoomConfig(context)
            kiwoomAppKey = kiwoomConfig.appKey
            kiwoomSecretKey = kiwoomConfig.secretKey
            kiwoomMode = kiwoomConfig.investmentMode

            val kisConfig = loadKisConfig(context)
            kisAppKey = kisConfig.appKey
            kisAppSecret = kisConfig.appSecret
            kisMode = kisConfig.investmentMode

            val krxCreds = loadKrxCredentials(context)
            krxId = krxCreds.id
            krxPassword = krxCreds.password

            val aiConfig = loadAiConfig(context)
            aiApiKey = aiConfig.apiKey
            aiProvider = aiConfig.provider
            aiModelId = aiConfig.modelId

            dartApiKey = loadDartApiKey(context)
            ecosApiKey = loadEcosApiKey(context)

            val keywordFilter = loadEtfKeywordFilter(context)
            includeKeywords = keywordFilter.includeKeywords
            excludeKeywords = keywordFilter.excludeKeywords

            val collectionPeriod = loadEtfCollectionPeriod(context)
            etfCollectionDays = collectionPeriod.daysBack

            val schedule = loadEtfScheduleTime(context)
            etfScheduleEnabled = schedule.enabled
            scheduleHour = schedule.hour
            scheduleMinute = schedule.minute

            val oscSchedule = loadOscillatorScheduleTime(context)
            oscScheduleEnabled = oscSchedule.enabled
            oscScheduleHour = oscSchedule.hour
            oscScheduleMinute = oscSchedule.minute

            val depositSchedule = loadDepositScheduleTime(context)
            depositScheduleEnabled = depositSchedule.enabled
            depositScheduleHour = depositSchedule.hour
            depositScheduleMinute = depositSchedule.minute

            val oscPeriod = loadMarketOscillatorCollectionPeriod(context)
            marketOscCollectionDays = oscPeriod.daysBack

            val depositPeriod = loadMarketDepositCollectionPeriod(context)
            marketDepositCollectionDays = depositPeriod.daysBack

            val mcRefreshSchedule = loadMarketCloseRefreshScheduleTime(context)
            marketCloseRefreshEnabled = mcRefreshSchedule.enabled
            marketCloseRefreshHour = mcRefreshSchedule.hour
            marketCloseRefreshMinute = mcRefreshSchedule.minute

            val consensusSchedule = loadConsensusScheduleTime(context)
            consensusScheduleEnabled = consensusSchedule.enabled
            consensusScheduleHour = consensusSchedule.hour
            consensusScheduleMinute = consensusSchedule.minute

            val consensusPeriod = loadConsensusCollectionPeriod(context)
            consensusCollectionDays = consensusPeriod.daysBack

            val fgSchedule = loadFearGreedScheduleTime(context)
            fgScheduleEnabled = fgSchedule.enabled
            fgScheduleHour = fgSchedule.hour
            fgScheduleMinute = fgSchedule.minute

            val fgPeriod = loadFearGreedCollectionPeriod(context)
            fearGreedCollectionDays = fgPeriod.daysBack

            // Worker execution logs
            val logDao = entryPoint.workerLogDao()
            lastEtfLog = logDao.getLatestLog(com.tinyoscillator.core.worker.EtfUpdateWorker.LABEL)
            lastOscLog = logDao.getLatestLog(com.tinyoscillator.core.worker.MarketOscillatorUpdateWorker.LABEL)
            lastDepositLog = logDao.getLatestLog(com.tinyoscillator.core.worker.MarketDepositUpdateWorker.LABEL)
            lastMarketCloseLog = logDao.getLatestLog(MarketCloseRefreshWorker.LABEL)
            lastConsensusLog = logDao.getLatestLog(ConsensusUpdateWorker.LABEL)
            lastFearGreedLog = logDao.getLatestLog(com.tinyoscillator.core.worker.FearGreedUpdateWorker.LABEL)
            lastIntegrityLog = logDao.getLatestLog(com.tinyoscillator.core.worker.DataIntegrityCheckWorker.LABEL)
            allLogs = logDao.getAllRecentLogs(200)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            saveMessage = "설정 로드 실패. 다시 입력해주세요."
        }
    }

    val themeModeState = LocalThemeModeState.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    ThemeToggleIcon(themeModeState)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollablePillTabRow(
                tabs = TAB_TITLES.indices.toList(),
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                tabLabel = { TAB_TITLES[it] }
            )

            when (selectedTab) {
                0 -> ApiTab(
                    kiwoomAppKey = kiwoomAppKey,
                    onKiwoomAppKeyChange = { kiwoomAppKey = it },
                    kiwoomSecretKey = kiwoomSecretKey,
                    onKiwoomSecretKeyChange = { kiwoomSecretKey = it },
                    kiwoomMode = kiwoomMode,
                    onKiwoomModeChange = { kiwoomMode = it },
                    kisAppKey = kisAppKey,
                    onKisAppKeyChange = { kisAppKey = it },
                    kisAppSecret = kisAppSecret,
                    onKisAppSecretChange = { kisAppSecret = it },
                    kisMode = kisMode,
                    onKisModeChange = { kisMode = it },
                    krxId = krxId,
                    onKrxIdChange = { krxId = it },
                    krxPassword = krxPassword,
                    onKrxPasswordChange = { krxPassword = it },
                    aiApiKey = aiApiKey,
                    onAiApiKeyChange = { aiApiKey = it },
                    aiProvider = aiProvider,
                    onAiProviderChange = { aiProvider = it; aiModelId = "" },
                    aiModelId = aiModelId,
                    onAiModelIdChange = { aiModelId = it },
                    dartApiKey = dartApiKey,
                    onDartApiKeyChange = { dartApiKey = it },
                    ecosApiKey = ecosApiKey,
                    onEcosApiKeyChange = { ecosApiKey = it },
                    saveMessage = saveMessage,
                    onSave = {
                        scope.launch {
                            saveMessage = saveApiSettings(
                                context, kiwoomAppKey, kiwoomSecretKey, kiwoomMode,
                                kisAppKey, kisAppSecret, kisMode, krxId, krxPassword
                            )
                            saveAiConfig(context, AiApiKeyConfig(aiProvider, aiApiKey, aiModelId))
                            saveDartApiKey(context, dartApiKey)
                            saveEcosApiKey(context, ecosApiKey)
                        }
                    }
                )
                1 -> EtfTab(
                    includeKeywords = includeKeywords,
                    excludeKeywords = excludeKeywords,
                    showAddIncludeDialog = showAddIncludeDialog,
                    showAddExcludeDialog = showAddExcludeDialog,
                    onIncludeRemove = { kw -> includeKeywords = includeKeywords - kw },
                    onExcludeRemove = { kw -> excludeKeywords = excludeKeywords - kw },
                    onShowAddInclude = { showAddIncludeDialog = true },
                    onShowAddExclude = { showAddExcludeDialog = true },
                    onAddInclude = { keyword ->
                        if (keyword.isNotBlank() && keyword !in includeKeywords) {
                            includeKeywords = includeKeywords + keyword.trim()
                        }
                        showAddIncludeDialog = false
                    },
                    onDismissInclude = { showAddIncludeDialog = false },
                    onAddExclude = { keyword ->
                        if (keyword.isNotBlank() && keyword !in excludeKeywords) {
                            excludeKeywords = excludeKeywords + keyword.trim()
                        }
                        showAddExcludeDialog = false
                    },
                    onDismissExclude = { showAddExcludeDialog = false },
                    saveMessage = saveMessage,
                    onSave = {
                        scope.launch {
                            saveMessage = saveEtfKeywordSettings(context, includeKeywords, excludeKeywords)
                        }
                    }
                )
                2 -> CollectionSettingsTab(
                    fearGreedCollectionDays = fearGreedCollectionDays,
                    onFearGreedCollectionDaysChange = { fearGreedCollectionDays = it },
                    etfCollectionDays = etfCollectionDays,
                    onEtfCollectionDaysChange = { etfCollectionDays = it },
                    marketOscCollectionDays = marketOscCollectionDays,
                    onMarketOscCollectionDaysChange = { marketOscCollectionDays = it },
                    marketDepositCollectionDays = marketDepositCollectionDays,
                    onMarketDepositCollectionDaysChange = { marketDepositCollectionDays = it },
                    consensusCollectionDays = consensusCollectionDays,
                    onConsensusCollectionDaysChange = { consensusCollectionDays = it },
                    saveMessage = saveMessage,
                    onSave = {
                        scope.launch {
                            saveMessage = saveCollectionSettings(
                                context, etfCollectionDays, marketOscCollectionDays,
                                marketDepositCollectionDays, consensusCollectionDays,
                                fearGreedCollectionDays
                            )
                        }
                    },
                    onResetData = { type ->
                        scope.launch {
                            val db = entryPoint.appDatabase()
                            withContext(Dispatchers.IO) {
                                when (type) {
                                    "feargreed" -> db.fearGreedDao().deleteAll()
                                    "etf" -> { db.etfDao().deleteAllHoldings(); db.etfDao().deleteAllEtfs() }
                                    "oscillator" -> db.marketOscillatorDao().deleteAll()
                                    "deposit" -> db.marketDepositDao().deleteAll()
                                    "consensus" -> db.consensusReportDao().deleteAll()
                                }
                            }
                            val label = when (type) {
                                "feargreed" -> "Fear & Greed"
                                "etf" -> "ETF"
                                "oscillator" -> "과매수/과매도"
                                "deposit" -> "자금 동향"
                                "consensus" -> "리포트"
                                else -> type
                            }
                            saveMessage = "$label 데이터가 초기화되었습니다"
                        }
                    },
                    showResetConfirmDialog = showResetConfirmDialog,
                    onShowResetConfirm = { showResetConfirmDialog = it },
                    onDismissResetConfirm = { showResetConfirmDialog = null }
                )
                3 -> ScheduleTab(
                    fgScheduleEnabled = fgScheduleEnabled,
                    onFgScheduleEnabledChange = { fgScheduleEnabled = it },
                    fgScheduleHour = fgScheduleHour,
                    onFgScheduleHourChange = { fgScheduleHour = it },
                    fgScheduleMinute = fgScheduleMinute,
                    onFgScheduleMinuteChange = { fgScheduleMinute = it },
                    fgManualMessage = fgCollectionState.message,
                    isFgCollecting = fgCollectionState.isCollecting,
                    onFgManualCollect = { WorkManagerHelper.runFearGreedUpdateNow(context) },
                    lastFearGreedLog = lastFearGreedLog,
                    etfScheduleEnabled = etfScheduleEnabled,
                    onEtfScheduleEnabledChange = { etfScheduleEnabled = it },
                    scheduleHour = scheduleHour,
                    onScheduleHourChange = { scheduleHour = it },
                    scheduleMinute = scheduleMinute,
                    onScheduleMinuteChange = { scheduleMinute = it },
                    manualCollectMessage = etfCollectionState.message,
                    etfCollectProgress = etfCollectionState.progress,
                    isEtfCollecting = etfCollectionState.isCollecting,
                    onManualCollect = { WorkManagerHelper.runEtfUpdateNow(context) },
                    oscScheduleEnabled = oscScheduleEnabled,
                    onOscScheduleEnabledChange = { oscScheduleEnabled = it },
                    oscScheduleHour = oscScheduleHour,
                    onOscScheduleHourChange = { oscScheduleHour = it },
                    oscScheduleMinute = oscScheduleMinute,
                    onOscScheduleMinuteChange = { oscScheduleMinute = it },
                    oscManualMessage = oscCollectionState.message,
                    isOscCollecting = oscCollectionState.isCollecting,
                    onOscManualCollect = { WorkManagerHelper.runOscillatorUpdateNow(context) },
                    depositScheduleEnabled = depositScheduleEnabled,
                    onDepositScheduleEnabledChange = { depositScheduleEnabled = it },
                    depositScheduleHour = depositScheduleHour,
                    onDepositScheduleHourChange = { depositScheduleHour = it },
                    depositScheduleMinute = depositScheduleMinute,
                    onDepositScheduleMinuteChange = { depositScheduleMinute = it },
                    depositManualMessage = depositCollectionState.message,
                    isDepositCollecting = depositCollectionState.isCollecting,
                    onDepositManualCollect = { WorkManagerHelper.runDepositUpdateNow(context) },
                    marketCloseRefreshEnabled = marketCloseRefreshEnabled,
                    onMarketCloseRefreshEnabledChange = { marketCloseRefreshEnabled = it },
                    marketCloseRefreshHour = marketCloseRefreshHour,
                    onMarketCloseRefreshHourChange = { marketCloseRefreshHour = it },
                    marketCloseRefreshMinute = marketCloseRefreshMinute,
                    onMarketCloseRefreshMinuteChange = { marketCloseRefreshMinute = it },
                    marketCloseRefreshMessage = marketCloseRefreshState.message,
                    marketCloseRefreshProgress = marketCloseRefreshState.progress,
                    isMarketCloseRefreshing = marketCloseRefreshState.isCollecting,
                    onMarketCloseRefreshManual = { WorkManagerHelper.runMarketCloseRefreshNow(context) },
                    integrityCheckMessage = integrityCheckState.message,
                    integrityCheckProgress = integrityCheckState.progress,
                    isIntegrityChecking = integrityCheckState.isCollecting,
                    onIntegrityCheck = { WorkManagerHelper.runIntegrityCheckNow(context) },
                    consensusScheduleEnabled = consensusScheduleEnabled,
                    onConsensusScheduleEnabledChange = { consensusScheduleEnabled = it },
                    consensusScheduleHour = consensusScheduleHour,
                    onConsensusScheduleHourChange = { consensusScheduleHour = it },
                    consensusScheduleMinute = consensusScheduleMinute,
                    onConsensusScheduleMinuteChange = { consensusScheduleMinute = it },
                    consensusManualMessage = consensusCollectionState.message,
                    isConsensusCollecting = consensusCollectionState.isCollecting,
                    onConsensusManualCollect = { WorkManagerHelper.runConsensusUpdateNow(context) },
                    lastEtfLog = lastEtfLog,
                    lastOscLog = lastOscLog,
                    lastDepositLog = lastDepositLog,
                    lastMarketCloseLog = lastMarketCloseLog,
                    lastConsensusLog = lastConsensusLog,
                    lastIntegrityLog = lastIntegrityLog,
                    saveMessage = saveMessage,
                    onSave = {
                        scope.launch {
                            saveMessage = saveScheduleSettings(
                                context, etfScheduleEnabled, scheduleHour, scheduleMinute,
                                oscScheduleEnabled, oscScheduleHour, oscScheduleMinute,
                                depositScheduleEnabled, depositScheduleHour, depositScheduleMinute,
                                marketCloseRefreshEnabled, marketCloseRefreshHour, marketCloseRefreshMinute,
                                consensusScheduleEnabled, consensusScheduleHour, consensusScheduleMinute,
                                fgScheduleEnabled, fgScheduleHour, fgScheduleMinute
                            )
                        }
                    }
                )
                4 -> {
                    val logContext = LocalContext.current
                    LogTab(
                        logs = allLogs,
                        logFilter = logFilter,
                        onFilterChange = { logFilter = it },
                        onExport = { exportLogs(logContext, allLogs) },
                        onClearLogs = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    entryPoint.workerLogDao().deleteAllLogs()
                                }
                                allLogs = emptyList()
                            }
                        },
                        onRefresh = {
                            scope.launch {
                                allLogs = withContext(Dispatchers.IO) {
                                    entryPoint.workerLogDao().getAllRecentLogs(200)
                                }
                            }
                        }
                    )
                }
                5 -> BackupTab(db = entryPoint.appDatabase())
            }
        }
    }
}

private suspend fun saveApiSettings(
    context: Context,
    kiwoomAppKey: String, kiwoomSecretKey: String, kiwoomMode: InvestmentMode,
    kisAppKey: String, kisAppSecret: String, kisMode: InvestmentMode,
    krxId: String, krxPassword: String
): String {
    return try {
        withContext(Dispatchers.IO) {
            getEncryptedPrefs(context).edit()
                .putString(PrefsKeys.KIWOOM_APP_KEY, kiwoomAppKey)
                .putString(PrefsKeys.KIWOOM_SECRET_KEY, kiwoomSecretKey)
                .putString(PrefsKeys.KIWOOM_MODE, kiwoomMode.name)
                .putString(PrefsKeys.KIS_APP_KEY, kisAppKey)
                .putString(PrefsKeys.KIS_APP_SECRET, kisAppSecret)
                .putString(PrefsKeys.KIS_MODE, kisMode.name)
                .putString(PrefsKeys.KRX_ID, krxId)
                .putString(PrefsKeys.KRX_PASSWORD, krxPassword)
                .apply()
        }
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}

private suspend fun saveEtfKeywordSettings(
    context: Context,
    includeKeywords: List<String>,
    excludeKeywords: List<String>
): String {
    return try {
        saveEtfKeywordFilter(context, EtfKeywordFilter(includeKeywords, excludeKeywords))
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}

private suspend fun saveScheduleSettings(
    context: Context,
    etfScheduleEnabled: Boolean, scheduleHour: Int, scheduleMinute: Int,
    oscScheduleEnabled: Boolean, oscScheduleHour: Int, oscScheduleMinute: Int,
    depositScheduleEnabled: Boolean, depositScheduleHour: Int, depositScheduleMinute: Int,
    marketCloseRefreshEnabled: Boolean = false, marketCloseRefreshHour: Int = 19, marketCloseRefreshMinute: Int = 0,
    consensusScheduleEnabled: Boolean = false, consensusScheduleHour: Int = 3, consensusScheduleMinute: Int = 0,
    fgScheduleEnabled: Boolean = false, fgScheduleHour: Int = 4, fgScheduleMinute: Int = 0
): String {
    return try {
        saveEtfScheduleTime(context, EtfScheduleTime(scheduleHour, scheduleMinute, etfScheduleEnabled))
        saveOscillatorScheduleTime(context, OscillatorScheduleTime(oscScheduleHour, oscScheduleMinute, oscScheduleEnabled))
        saveDepositScheduleTime(context, DepositScheduleTime(depositScheduleHour, depositScheduleMinute, depositScheduleEnabled))
        saveMarketCloseRefreshScheduleTime(context, MarketCloseRefreshScheduleTime(marketCloseRefreshHour, marketCloseRefreshMinute, marketCloseRefreshEnabled))
        saveConsensusScheduleTime(context, ConsensusScheduleTime(consensusScheduleHour, consensusScheduleMinute, consensusScheduleEnabled))
        saveFearGreedScheduleTime(context, FearGreedScheduleTime(fgScheduleHour, fgScheduleMinute, fgScheduleEnabled))
        if (etfScheduleEnabled) {
            WorkManagerHelper.scheduleEtfUpdate(context, scheduleHour, scheduleMinute)
        } else {
            WorkManagerHelper.cancelEtfUpdate(context)
        }
        if (oscScheduleEnabled) {
            WorkManagerHelper.scheduleOscillatorUpdate(context, oscScheduleHour, oscScheduleMinute)
        } else {
            WorkManagerHelper.cancelOscillatorUpdate(context)
        }
        if (depositScheduleEnabled) {
            WorkManagerHelper.scheduleDepositUpdate(context, depositScheduleHour, depositScheduleMinute)
        } else {
            WorkManagerHelper.cancelDepositUpdate(context)
        }
        if (marketCloseRefreshEnabled) {
            WorkManagerHelper.scheduleMarketCloseRefresh(context, marketCloseRefreshHour, marketCloseRefreshMinute)
        } else {
            WorkManagerHelper.cancelMarketCloseRefresh(context)
        }
        if (consensusScheduleEnabled) {
            WorkManagerHelper.scheduleConsensusUpdate(context, consensusScheduleHour, consensusScheduleMinute)
        } else {
            WorkManagerHelper.cancelConsensusUpdate(context)
        }
        if (fgScheduleEnabled) {
            WorkManagerHelper.scheduleFearGreedUpdate(context, fgScheduleHour, fgScheduleMinute)
        } else {
            WorkManagerHelper.cancelFearGreedUpdate(context)
        }
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}

private suspend fun saveCollectionSettings(
    context: Context,
    etfDays: Int,
    oscDays: Int,
    depositDays: Int,
    consensusDays: Int,
    fearGreedDays: Int = 365
): String {
    return try {
        saveEtfCollectionPeriod(context, EtfCollectionPeriod(etfDays))
        saveMarketOscillatorCollectionPeriod(context, MarketOscillatorCollectionPeriod(oscDays))
        saveMarketDepositCollectionPeriod(context, MarketDepositCollectionPeriod(depositDays))
        saveConsensusCollectionPeriod(context, ConsensusCollectionPeriod(consensusDays))
        saveFearGreedCollectionPeriod(context, FearGreedCollectionPeriod(fearGreedDays))
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}
