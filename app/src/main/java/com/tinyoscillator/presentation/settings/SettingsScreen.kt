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
import com.tinyoscillator.core.worker.DataIntegrityCheckWorker
import com.tinyoscillator.core.worker.WorkManagerHelper
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
}

// KrxCredentials and EtfKeywordFilter moved to domain.model.EtfModels.kt
// Re-export for backward compatibility
typealias KrxCredentials = com.tinyoscillator.domain.model.KrxCredentials
typealias EtfKeywordFilter = com.tinyoscillator.domain.model.EtfKeywordFilter

data class EtfScheduleTime(val hour: Int = 0, val minute: Int = 30, val enabled: Boolean = true)

data class OscillatorScheduleTime(val hour: Int = 1, val minute: Int = 0, val enabled: Boolean = false)

data class DepositScheduleTime(val hour: Int = 2, val minute: Int = 0, val enabled: Boolean = false)

data class EtfCollectionPeriod(val daysBack: Int = 14)

data class MarketOscillatorCollectionPeriod(val daysBack: Int = 30)
data class MarketDepositCollectionPeriod(val daysBack: Int = 365)

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
    val providerName = prefs.getString(PrefsKeys.AI_PROVIDER, AiProvider.CLAUDE_HAIKU.name) ?: AiProvider.CLAUDE_HAIKU.name
    AiApiKeyConfig(
        provider = AiProvider.entries.find { it.name == providerName } ?: AiProvider.CLAUDE_HAIKU,
        apiKey = prefs.getString(PrefsKeys.AI_API_KEY, "") ?: ""
    )
}

suspend fun saveAiConfig(context: Context, config: AiApiKeyConfig) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putString(PrefsKeys.AI_PROVIDER, config.provider.name)
        .putString(PrefsKeys.AI_API_KEY, config.apiKey)
        .apply()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun appDatabase(): AppDatabase
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

private val TAB_TITLES = listOf("API", "ETF", "시장지표", "Schedule", "Backup")

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
    var aiProvider by remember { mutableStateOf(AiProvider.CLAUDE_HAIKU) }

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

    var marketOscCollectionDays by remember { mutableIntStateOf(30) }
    var marketDepositCollectionDays by remember { mutableIntStateOf(365) }

    var saveMessage by remember { mutableStateOf<String?>(null) }

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

    val etfCollectionState = rememberCollectionState(etfWorkInfos)
    val oscCollectionState = rememberCollectionState(oscWorkInfos)
    val depositCollectionState = rememberCollectionState(depositWorkInfos)
    val integrityCheckState = rememberCollectionState(integrityWorkInfos)

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
                    onAiProviderChange = { aiProvider = it },
                    saveMessage = saveMessage,
                    onSave = {
                        scope.launch {
                            saveMessage = saveApiSettings(
                                context, kiwoomAppKey, kiwoomSecretKey, kiwoomMode,
                                kisAppKey, kisAppSecret, kisMode, krxId, krxPassword
                            )
                            saveAiConfig(context, AiApiKeyConfig(aiProvider, aiApiKey))
                        }
                    }
                )
                1 -> EtfTab(
                    includeKeywords = includeKeywords,
                    excludeKeywords = excludeKeywords,
                    etfCollectionDays = etfCollectionDays,
                    onEtfCollectionDaysChange = { etfCollectionDays = it },
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
                    etfCollectProgress = etfCollectionState.progress,
                    isEtfCollecting = etfCollectionState.isCollecting,
                    onSave = {
                        scope.launch {
                            saveMessage = saveEtfSettings(context, includeKeywords, excludeKeywords, etfCollectionDays)
                        }
                    }
                )
                2 -> MarketIndicatorTab(
                    marketOscCollectionDays = marketOscCollectionDays,
                    onMarketOscCollectionDaysChange = { marketOscCollectionDays = it },
                    marketDepositCollectionDays = marketDepositCollectionDays,
                    onMarketDepositCollectionDaysChange = { marketDepositCollectionDays = it },
                    saveMessage = saveMessage,
                    onSave = { oscDays, depositDays ->
                        scope.launch {
                            saveMessage = saveMarketIndicatorSettings(context, oscDays, depositDays)
                        }
                    }
                )
                3 -> ScheduleTab(
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
                    integrityCheckMessage = integrityCheckState.message,
                    integrityCheckProgress = integrityCheckState.progress,
                    isIntegrityChecking = integrityCheckState.isCollecting,
                    onIntegrityCheck = { WorkManagerHelper.runIntegrityCheckNow(context) },
                    saveMessage = saveMessage,
                    onSave = {
                        scope.launch {
                            saveMessage = saveScheduleSettings(
                                context, etfScheduleEnabled, scheduleHour, scheduleMinute,
                                oscScheduleEnabled, oscScheduleHour, oscScheduleMinute,
                                depositScheduleEnabled, depositScheduleHour, depositScheduleMinute
                            )
                        }
                    }
                )
                4 -> BackupTab(db = entryPoint.appDatabase())
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

private suspend fun saveEtfSettings(
    context: Context,
    includeKeywords: List<String>,
    excludeKeywords: List<String>,
    collectionDays: Int
): String {
    return try {
        saveEtfKeywordFilter(context, EtfKeywordFilter(includeKeywords, excludeKeywords))
        saveEtfCollectionPeriod(context, EtfCollectionPeriod(collectionDays))
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
    depositScheduleEnabled: Boolean, depositScheduleHour: Int, depositScheduleMinute: Int
): String {
    return try {
        saveEtfScheduleTime(context, EtfScheduleTime(scheduleHour, scheduleMinute, etfScheduleEnabled))
        saveOscillatorScheduleTime(context, OscillatorScheduleTime(oscScheduleHour, oscScheduleMinute, oscScheduleEnabled))
        saveDepositScheduleTime(context, DepositScheduleTime(depositScheduleHour, depositScheduleMinute, depositScheduleEnabled))
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
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}

private suspend fun saveMarketIndicatorSettings(
    context: Context,
    oscDays: Int,
    depositDays: Int
): String {
    return try {
        saveMarketOscillatorCollectionPeriod(context, MarketOscillatorCollectionPeriod(oscDays))
        saveMarketDepositCollectionPeriod(context, MarketDepositCollectionPeriod(depositDays))
        "저장되었습니다"
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        "저장 실패. 다시 시도해주세요."
    }
}
