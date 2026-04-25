package com.tinyoscillator.presentation.settings

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
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.domain.model.AiApiKeyConfig
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.domain.model.ThemeExchange
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.core.worker.EtfUpdateWorker
import com.tinyoscillator.core.worker.KEY_MESSAGE
import com.tinyoscillator.core.worker.KEY_PROGRESS
import com.tinyoscillator.core.worker.MarketDepositUpdateWorker
import com.tinyoscillator.core.worker.MarketOscillatorUpdateWorker
import com.tinyoscillator.core.worker.ConsensusUpdateWorker
import com.tinyoscillator.core.worker.DataIntegrityCheckWorker
import com.tinyoscillator.core.worker.MarketCloseRefreshWorker
import com.tinyoscillator.core.worker.ThemeUpdateWorker
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

    var themeScheduleEnabled by remember { mutableStateOf(false) }
    var themeScheduleHour by remember { mutableIntStateOf(2) }
    var themeScheduleMinute by remember { mutableIntStateOf(30) }
    var themeExchange by remember { mutableStateOf(ThemeExchange.KRX) }

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
    var lastThemeLog by remember { mutableStateOf<WorkerLogEntity?>(null) }
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
    val themeWorkInfos by workManager.getWorkInfosByTagFlow(ThemeUpdateWorker.TAG)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val etfCollectionState = rememberCollectionState(etfWorkInfos)
    val oscCollectionState = rememberCollectionState(oscWorkInfos)
    val depositCollectionState = rememberCollectionState(depositWorkInfos)
    val integrityCheckState = rememberCollectionState(integrityWorkInfos)
    val marketCloseRefreshState = rememberCollectionState(marketCloseRefreshWorkInfos)
    val consensusCollectionState = rememberCollectionState(consensusWorkInfos)
    val fgCollectionState = rememberCollectionState(fgWorkInfos)
    val themeCollectionState = rememberCollectionState(themeWorkInfos)

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

            val themeSchedule = loadThemeScheduleTime(context)
            themeScheduleEnabled = themeSchedule.enabled
            themeScheduleHour = themeSchedule.hour
            themeScheduleMinute = themeSchedule.minute

            themeExchange = loadThemeExchangeFilter(context)

            // Worker execution logs
            val logDao = entryPoint.workerLogDao()
            lastEtfLog = logDao.getLatestLog(com.tinyoscillator.core.worker.EtfUpdateWorker.LABEL)
            lastOscLog = logDao.getLatestLog(com.tinyoscillator.core.worker.MarketOscillatorUpdateWorker.LABEL)
            lastDepositLog = logDao.getLatestLog(com.tinyoscillator.core.worker.MarketDepositUpdateWorker.LABEL)
            lastMarketCloseLog = logDao.getLatestLog(MarketCloseRefreshWorker.LABEL)
            lastConsensusLog = logDao.getLatestLog(ConsensusUpdateWorker.LABEL)
            lastFearGreedLog = logDao.getLatestLog(com.tinyoscillator.core.worker.FearGreedUpdateWorker.LABEL)
            lastThemeLog = logDao.getLatestLog(ThemeUpdateWorker.LABEL)
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
                    themeScheduleEnabled = themeScheduleEnabled,
                    onThemeScheduleEnabledChange = { themeScheduleEnabled = it },
                    themeScheduleHour = themeScheduleHour,
                    onThemeScheduleHourChange = { themeScheduleHour = it },
                    themeScheduleMinute = themeScheduleMinute,
                    onThemeScheduleMinuteChange = { themeScheduleMinute = it },
                    themeExchange = themeExchange,
                    onThemeExchangeChange = { themeExchange = it },
                    themeManualMessage = themeCollectionState.message,
                    isThemeCollecting = themeCollectionState.isCollecting,
                    onThemeManualCollect = { WorkManagerHelper.runThemeUpdateNow(context) },
                    lastThemeLog = lastThemeLog,
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
                                fgScheduleEnabled, fgScheduleHour, fgScheduleMinute,
                                themeScheduleEnabled, themeScheduleHour, themeScheduleMinute,
                                themeExchange
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
