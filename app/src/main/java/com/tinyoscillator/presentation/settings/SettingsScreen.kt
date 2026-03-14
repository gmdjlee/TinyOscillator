package com.tinyoscillator.presentation.settings

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
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

private object PrefsKeys {
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

data class KrxCredentials(val id: String, val password: String)

data class EtfKeywordFilter(
    val includeKeywords: List<String>,
    val excludeKeywords: List<String>
)

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

private fun getEncryptedPrefs(context: Context): SharedPreferences {
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

    val etfCollectionState = rememberCollectionState(etfWorkInfos)
    val oscCollectionState = rememberCollectionState(oscWorkInfos)
    val depositCollectionState = rememberCollectionState(depositWorkInfos)

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                TAB_TITLES.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarketIndicatorTab(
    marketOscCollectionDays: Int,
    onMarketOscCollectionDaysChange: (Int) -> Unit,
    marketDepositCollectionDays: Int,
    onMarketDepositCollectionDaysChange: (Int) -> Unit,
    saveMessage: String?,
    onSave: (oscDays: Int, depositDays: Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === 과매수/과매도 수집 기간 ===
        Text("과매수/과매도 데이터 수집 기간", style = MaterialTheme.typography.titleMedium)

        CollectionPeriodRow(
            daysBack = marketOscCollectionDays,
            onDaysBackChange = onMarketOscCollectionDaysChange,
            onSave = { onSave(marketOscCollectionDays, marketDepositCollectionDays) }
        )

        Text(
            "초기 수집 또는 전체 새로고침 시 수집할 기간입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // === 자금 동향 수집 기간 ===
        Text("자금 동향 데이터 수집 기간", style = MaterialTheme.typography.titleMedium)

        CollectionPeriodRow(
            daysBack = marketDepositCollectionDays,
            onDaysBackChange = onMarketDepositCollectionDaysChange,
            onSave = { onSave(marketOscCollectionDays, marketDepositCollectionDays) }
        )

        Text(
            "초기 수집 또는 전체 새로고침 시 수집할 기간입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        saveMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionPeriodRow(
    daysBack: Int,
    onDaysBackChange: (Int) -> Unit,
    onSave: () -> Unit
) {
    val unitOptions = listOf("주" to 7, "월" to 30, "년" to 365)

    val initialUnit = when {
        daysBack % 365 == 0 -> 365
        daysBack % 30 == 0 -> 30
        daysBack % 7 == 0 -> 7
        else -> 30
    }
    val initialValue = when (initialUnit) {
        365 -> daysBack / 365
        30 -> daysBack / 30
        7 -> daysBack / 7
        else -> daysBack / 30
    }.coerceAtLeast(1)

    var selectedUnitMultiplier by remember(daysBack) { mutableIntStateOf(initialUnit) }
    var periodValue by remember(daysBack) { mutableStateOf(initialValue.toString()) }
    var unitExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = periodValue,
            onValueChange = { v ->
                val filtered = v.filter { it.isDigit() }.take(3)
                periodValue = filtered
                filtered.toIntOrNull()?.let { num ->
                    if (num > 0) onDaysBackChange(num * selectedUnitMultiplier)
                }
            },
            label = { Text("기간") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.weight(1f)
        )

        ExposedDropdownMenuBox(
            expanded = unitExpanded,
            onExpandedChange = { unitExpanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = unitOptions.first { it.second == selectedUnitMultiplier }.first,
                onValueChange = {},
                readOnly = true,
                label = { Text("단위") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = unitExpanded,
                onDismissRequest = { unitExpanded = false }
            ) {
                unitOptions.forEach { (label, multiplier) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            selectedUnitMultiplier = multiplier
                            unitExpanded = false
                            periodValue.toIntOrNull()?.let { num ->
                                if (num > 0) onDaysBackChange(num * multiplier)
                            }
                        }
                    )
                }
            }
        }

        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f)
        ) {
            Text("저장")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiTab(
    kiwoomAppKey: String, onKiwoomAppKeyChange: (String) -> Unit,
    kiwoomSecretKey: String, onKiwoomSecretKeyChange: (String) -> Unit,
    kiwoomMode: InvestmentMode, onKiwoomModeChange: (InvestmentMode) -> Unit,
    kisAppKey: String, onKisAppKeyChange: (String) -> Unit,
    kisAppSecret: String, onKisAppSecretChange: (String) -> Unit,
    kisMode: InvestmentMode, onKisModeChange: (InvestmentMode) -> Unit,
    krxId: String, onKrxIdChange: (String) -> Unit,
    krxPassword: String, onKrxPasswordChange: (String) -> Unit,
    aiApiKey: String, onAiApiKeyChange: (String) -> Unit,
    aiProvider: AiProvider, onAiProviderChange: (AiProvider) -> Unit,
    saveMessage: String?,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === Kiwoom API ===
        Text("Kiwoom API", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = kiwoomAppKey,
            onValueChange = onKiwoomAppKeyChange,
            label = { Text("App Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = kiwoomSecretKey,
            onValueChange = onKiwoomSecretKeyChange,
            label = { Text("Secret Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InvestmentMode.entries.forEach { mode ->
                FilterChip(
                    selected = kiwoomMode == mode,
                    onClick = { onKiwoomModeChange(mode) },
                    label = { Text(mode.displayName) }
                )
            }
        }

        HorizontalDivider()

        // === KIS API ===
        Text("KIS API (한국투자증권)", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = kisAppKey,
            onValueChange = onKisAppKeyChange,
            label = { Text("App Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = kisAppSecret,
            onValueChange = onKisAppSecretChange,
            label = { Text("App Secret") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InvestmentMode.entries.forEach { mode ->
                FilterChip(
                    selected = kisMode == mode,
                    onClick = { onKisModeChange(mode) },
                    label = { Text(mode.displayName) }
                )
            }
        }

        HorizontalDivider()

        // === KRX API ===
        Text("KRX 데이터 (ETF분석용)", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = krxId,
            onValueChange = onKrxIdChange,
            label = { Text("KRX ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = krxPassword,
            onValueChange = onKrxPasswordChange,
            label = { Text("KRX 비밀번호") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        // === AI API ===
        Text("AI 분석 (Claude / Gemini)", style = MaterialTheme.typography.titleMedium)

        var providerExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = it }
        ) {
            OutlinedTextField(
                value = aiProvider.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("AI Provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = providerExpanded,
                onDismissRequest = { providerExpanded = false }
            ) {
                AiProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName) },
                        onClick = {
                            onAiProviderChange(provider)
                            providerExpanded = false
                        }
                    )
                }
            }
        }

        OutlinedTextField(
            value = aiApiKey,
            onValueChange = onAiApiKeyChange,
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                "• Claude: anthropic.com에서 API Key 발급\n• Gemini: aistudio.google.com에서 API Key 발급\n• 권장: Claude Haiku (가장 저렴, 월 ~₩1,100)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        }

        HorizontalDivider()

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("저장")
        }

        saveMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EtfTab(
    includeKeywords: List<String>,
    excludeKeywords: List<String>,
    etfCollectionDays: Int,
    onEtfCollectionDaysChange: (Int) -> Unit,
    showAddIncludeDialog: Boolean,
    showAddExcludeDialog: Boolean,
    onIncludeRemove: (String) -> Unit,
    onExcludeRemove: (String) -> Unit,
    onShowAddInclude: () -> Unit,
    onShowAddExclude: () -> Unit,
    onAddInclude: (String) -> Unit,
    onDismissInclude: () -> Unit,
    onAddExclude: (String) -> Unit,
    onDismissExclude: () -> Unit,
    saveMessage: String?,
    etfCollectProgress: Float?,
    isEtfCollecting: Boolean,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ETF 키워드 필터", style = MaterialTheme.typography.titleMedium)

        Text("포함 키워드", style = MaterialTheme.typography.bodyMedium)
        KeywordChipRow(
            keywords = includeKeywords,
            onRemove = onIncludeRemove,
            onAdd = onShowAddInclude
        )

        Text("제외 키워드", style = MaterialTheme.typography.bodyMedium)
        KeywordChipRow(
            keywords = excludeKeywords,
            onRemove = onExcludeRemove,
            onAdd = onShowAddExclude
        )

        if (showAddIncludeDialog) {
            AddKeywordDialog(
                title = "포함 키워드 추가",
                onDismiss = onDismissInclude,
                onConfirm = onAddInclude
            )
        }

        if (showAddExcludeDialog) {
            AddKeywordDialog(
                title = "제외 키워드 추가",
                onDismiss = onDismissExclude,
                onConfirm = onAddExclude
            )
        }

        HorizontalDivider()

        Text("데이터 수집 기간", style = MaterialTheme.typography.titleMedium)

        var isWeekUnit by remember { mutableStateOf(etfCollectionDays % 7 == 0 && etfCollectionDays / 7 <= 4) }
        var periodValue by remember {
            mutableStateOf(
                if (etfCollectionDays % 7 == 0 && etfCollectionDays / 7 <= 4)
                    (etfCollectionDays / 7).toString()
                else
                    (etfCollectionDays / 30).coerceAtLeast(1).toString()
            )
        }
        val unitOptions = listOf("주" to true, "월" to false)
        var unitExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = periodValue,
                onValueChange = { v ->
                    val filtered = v.filter { it.isDigit() }.take(2)
                    periodValue = filtered
                    filtered.toIntOrNull()?.let { num ->
                        if (num > 0) {
                            val days = if (isWeekUnit) num * 7 else num * 30
                            onEtfCollectionDaysChange(days)
                        }
                    }
                },
                label = { Text("기간") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )

            ExposedDropdownMenuBox(
                expanded = unitExpanded,
                onExpandedChange = { unitExpanded = it },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = if (isWeekUnit) "주" else "월",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("단위") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = unitExpanded,
                    onDismissRequest = { unitExpanded = false }
                ) {
                    unitOptions.forEach { (label, isWeek) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                isWeekUnit = isWeek
                                unitExpanded = false
                                periodValue.toIntOrNull()?.let { num ->
                                    if (num > 0) onEtfCollectionDaysChange(if (isWeek) num * 7 else num * 30)
                                }
                            }
                        )
                    }
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = !isEtfCollecting
            ) {
                Text("저장")
            }
        }

        Text(
            "앱 첫 실행 시 또는 전체 새로고침 시 수집할 기간입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        etfCollectProgress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        saveMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ScheduleSection(
    title: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    hour: Int,
    onHourChange: (Int) -> Unit,
    minute: Int,
    onMinuteChange: (Int) -> Unit,
    manualButtonText: String,
    onManualCollect: () -> Unit,
    message: String?,
    progress: Float? = null,
    isCollecting: Boolean = false
) {
    Text(title, style = MaterialTheme.typography.titleMedium)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("자동 업데이트 활성화", style = MaterialTheme.typography.bodyMedium)
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }

    if (enabled) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = "%02d".format(hour),
                onValueChange = { v ->
                    v.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                        if (it in 0..23) onHourChange(it)
                    }
                },
                label = { Text("시") },
                singleLine = true,
                modifier = Modifier.width(80.dp)
            )
            Text(":", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = "%02d".format(minute),
                onValueChange = { v ->
                    v.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                        if (it in 0..59) onMinuteChange(it)
                    }
                },
                label = { Text("분") },
                singleLine = true,
                modifier = Modifier.width(80.dp)
            )
            Text(
                "매일 자동 업데이트",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    OutlinedButton(
        onClick = onManualCollect,
        modifier = Modifier.fillMaxWidth(),
        enabled = !isCollecting
    ) {
        Text(manualButtonText)
    }

    progress?.let { p ->
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier.fillMaxWidth()
        )
    }

    message?.let { msg ->
        Text(
            text = msg,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ScheduleTab(
    etfScheduleEnabled: Boolean,
    onEtfScheduleEnabledChange: (Boolean) -> Unit,
    scheduleHour: Int,
    onScheduleHourChange: (Int) -> Unit,
    scheduleMinute: Int,
    onScheduleMinuteChange: (Int) -> Unit,
    manualCollectMessage: String?,
    etfCollectProgress: Float?,
    isEtfCollecting: Boolean,
    onManualCollect: () -> Unit,
    oscScheduleEnabled: Boolean,
    onOscScheduleEnabledChange: (Boolean) -> Unit,
    oscScheduleHour: Int,
    onOscScheduleHourChange: (Int) -> Unit,
    oscScheduleMinute: Int,
    onOscScheduleMinuteChange: (Int) -> Unit,
    oscManualMessage: String?,
    isOscCollecting: Boolean,
    onOscManualCollect: () -> Unit,
    depositScheduleEnabled: Boolean,
    onDepositScheduleEnabledChange: (Boolean) -> Unit,
    depositScheduleHour: Int,
    onDepositScheduleHourChange: (Int) -> Unit,
    depositScheduleMinute: Int,
    onDepositScheduleMinuteChange: (Int) -> Unit,
    depositManualMessage: String?,
    isDepositCollecting: Boolean,
    onDepositManualCollect: () -> Unit,
    saveMessage: String?,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScheduleSection(
            title = "ETF 자동 업데이트",
            enabled = etfScheduleEnabled,
            onEnabledChange = onEtfScheduleEnabledChange,
            hour = scheduleHour,
            onHourChange = onScheduleHourChange,
            minute = scheduleMinute,
            onMinuteChange = onScheduleMinuteChange,
            manualButtonText = "지금 ETF 데이터 수집",
            onManualCollect = onManualCollect,
            message = manualCollectMessage,
            progress = etfCollectProgress,
            isCollecting = isEtfCollecting
        )

        HorizontalDivider()

        ScheduleSection(
            title = "과매수/과매도 자동 업데이트",
            enabled = oscScheduleEnabled,
            onEnabledChange = onOscScheduleEnabledChange,
            hour = oscScheduleHour,
            onHourChange = onOscScheduleHourChange,
            minute = oscScheduleMinute,
            onMinuteChange = onOscScheduleMinuteChange,
            manualButtonText = "지금 과매수/과매도 업데이트",
            onManualCollect = onOscManualCollect,
            message = oscManualMessage,
            isCollecting = isOscCollecting
        )

        HorizontalDivider()

        ScheduleSection(
            title = "자금 동향 자동 업데이트",
            enabled = depositScheduleEnabled,
            onEnabledChange = onDepositScheduleEnabledChange,
            hour = depositScheduleHour,
            onHourChange = onDepositScheduleHourChange,
            minute = depositScheduleMinute,
            onMinuteChange = onDepositScheduleMinuteChange,
            manualButtonText = "지금 자금 동향 업데이트",
            onManualCollect = onDepositManualCollect,
            message = depositManualMessage,
            isCollecting = isDepositCollecting
        )

        HorizontalDivider()

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text("저장")
        }

        saveMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun BackupTab(db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // ETF date range
    var etfDateRangeEnabled by remember { mutableStateOf(false) }
    var etfStartDate by remember { mutableStateOf("") }
    var etfEndDate by remember { mutableStateOf("") }

    // Load available date range
    var availableDates by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            availableDates = db.etfDao().getAllDates()
        }
        if (availableDates.isNotEmpty()) {
            etfStartDate = availableDates.last()
            etfEndDate = availableDates.first()
        }
    }

    // API backup launchers
    var pendingApiExportType by remember { mutableStateOf("") }
    var pendingApiPassword by remember { mutableStateOf("") }
    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var showImportPasswordDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val apiExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let {
            val pw = pendingApiPassword
            scope.launch {
                isProcessing = true
                val result = BackupManager.exportApiBackup(context, it, pendingApiExportType, pw)
                backupMessage = result.fold(
                    onSuccess = { "API 백업 완료 (암호화)" },
                    onFailure = { e -> "백업 실패: ${e.message}" }
                )
                isProcessing = false
            }
        }
    }

    val apiImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingImportUri = it
            showImportPasswordDialog = true
        }
    }

    // Portfolio backup launchers
    val portfolioExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                val result = BackupManager.exportPortfolioData(context, it, db)
                backupMessage = result.fold(
                    onSuccess = { count -> "포트폴리오 백업 완료 (거래 ${count}건)" },
                    onFailure = { e -> "백업 실패: ${e.message}" }
                )
                isProcessing = false
            }
        }
    }

    val portfolioImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                val result = BackupManager.importPortfolioData(context, it, db)
                backupMessage = result.fold(
                    onSuccess = { msg -> msg },
                    onFailure = { e -> "복원 실패: ${e.message}" }
                )
                isProcessing = false
            }
        }
    }

    // ETF backup launchers
    val etfExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                val start = if (etfDateRangeEnabled) etfStartDate else null
                val end = if (etfDateRangeEnabled) etfEndDate else null
                val result = BackupManager.exportEtfData(context, it, db, start, end)
                backupMessage = result.fold(
                    onSuccess = { count -> "ETF 백업 완료 (보유종목 ${count}건)" },
                    onFailure = { e -> "백업 실패: ${e.message}" }
                )
                isProcessing = false
            }
        }
    }

    val etfImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                val result = BackupManager.importEtfData(context, it, db)
                backupMessage = result.fold(
                    onSuccess = { msg -> msg },
                    onFailure = { e -> "복원 실패: ${e.message}" }
                )
                isProcessing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // === API 키 백업 ===
        Text("API 키 백업/복원", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    pendingApiExportType = "kiwoom"
                    showExportPasswordDialog = true
                },
                enabled = !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Text("Kiwoom", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    pendingApiExportType = "kis"
                    showExportPasswordDialog = true
                },
                enabled = !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Text("KIS", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    pendingApiExportType = "krx"
                    showExportPasswordDialog = true
                },
                enabled = !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Text("KRX", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = {
                    pendingApiExportType = "ai"
                    showExportPasswordDialog = true
                },
                enabled = !isProcessing,
                modifier = Modifier.weight(1f)
            ) {
                Text("AI", style = MaterialTheme.typography.labelSmall)
            }
        }

        Button(
            onClick = {
                pendingApiExportType = "all_api"
                showExportPasswordDialog = true
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("전체 API 백업")
        }

        OutlinedButton(
            onClick = { apiImportLauncher.launch(arrayOf("application/octet-stream", "application/json")) },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("API 백업 복원")
        }

        HorizontalDivider()

        // === ETF 데이터 백업 ===
        Text("ETF 데이터 백업/복원", style = MaterialTheme.typography.titleMedium)

        if (availableDates.isNotEmpty()) {
            Text(
                "DB 저장 기간: ${availableDates.last()} ~ ${availableDates.first()} (${availableDates.size}일)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("기간 지정", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = etfDateRangeEnabled,
                onCheckedChange = { etfDateRangeEnabled = it }
            )
        }

        if (etfDateRangeEnabled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = etfStartDate,
                    onValueChange = { etfStartDate = it },
                    label = { Text("시작일") },
                    placeholder = { Text("yyyy-MM-dd") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text("~")
                OutlinedTextField(
                    value = etfEndDate,
                    onValueChange = { etfEndDate = it },
                    label = { Text("종료일") },
                    placeholder = { Text("yyyy-MM-dd") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Button(
            onClick = {
                val fileName = if (etfDateRangeEnabled) {
                    "etf_backup_${etfStartDate}_${etfEndDate}.json"
                } else {
                    "etf_backup_all.json"
                }
                etfExportLauncher.launch(fileName)
            },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (etfDateRangeEnabled) "기간 ETF 백업" else "전체 ETF 백업")
        }

        OutlinedButton(
            onClick = { etfImportLauncher.launch(arrayOf("application/json")) },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("ETF 백업 복원")
        }

        HorizontalDivider()

        // === 포트폴리오 데이터 백업 ===
        Text("포트폴리오 백업/복원", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = { portfolioExportLauncher.launch("portfolio_backup.json") },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("포트폴리오 내보내기")
        }

        OutlinedButton(
            onClick = { portfolioImportLauncher.launch(arrayOf("application/json")) },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("포트폴리오 가져오기")
        }

        if (isProcessing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        backupMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Export password dialog
    if (showExportPasswordDialog) {
        PasswordInputDialog(
            isExport = true,
            onConfirm = { pw ->
                showExportPasswordDialog = false
                pendingApiPassword = pw
                val filename = when (pendingApiExportType) {
                    "kiwoom" -> "kiwoom_api_backup.enc"
                    "kis" -> "kis_api_backup.enc"
                    "krx" -> "krx_api_backup.enc"
                    else -> "api_backup_all.enc"
                }
                apiExportLauncher.launch(filename)
            },
            onDismiss = { showExportPasswordDialog = false }
        )
    }

    // Import password dialog
    if (showImportPasswordDialog) {
        PasswordInputDialog(
            isExport = false,
            onConfirm = { pw ->
                showImportPasswordDialog = false
                val uri = pendingImportUri ?: return@PasswordInputDialog
                scope.launch {
                    isProcessing = true
                    val result = BackupManager.importApiBackup(context, uri, pw)
                    backupMessage = result.fold(
                        onSuccess = { msg -> msg },
                        onFailure = { e -> "복원 실패: ${e.message}" }
                    )
                    isProcessing = false
                    pendingImportUri = null
                }
            },
            onDismiss = {
                showImportPasswordDialog = false
                pendingImportUri = null
            }
        )
    }
}

@Composable
private fun PasswordInputDialog(
    isExport: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isExport) "백업 비밀번호 설정" else "백업 비밀번호 입력") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isExport) {
                    Text(
                        "API 키를 암호화하여 저장합니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("비밀번호") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isExport) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text("비밀번호 확인") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    password.isEmpty() -> errorMessage = "비밀번호를 입력하세요"
                    isExport && password != confirmPassword -> errorMessage = "비밀번호가 일치하지 않습니다"
                    else -> onConfirm(password)
                }
            }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordChipRow(
    keywords: List<String>,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        keywords.forEach { keyword ->
            FilterChip(
                selected = true,
                onClick = { onRemove(keyword) },
                label = { Text(keyword) },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "삭제",
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = "추가")
        }
    }
}

@Composable
private fun AddKeywordDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("키워드") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
