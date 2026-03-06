package com.tinyoscillator.presentation.settings

import android.content.Context
import android.content.SharedPreferences
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
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.core.worker.WorkManagerHelper
import kotlinx.coroutines.Dispatchers
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
    const val ETF_INCLUDE_KEYWORDS = "etf_include_keywords"
    const val ETF_EXCLUDE_KEYWORDS = "etf_exclude_keywords"
    const val ETF_SCHEDULE_HOUR = "etf_schedule_hour"
    const val ETF_SCHEDULE_MINUTE = "etf_schedule_minute"
    const val OSCILLATOR_SCHEDULE_HOUR = "oscillator_schedule_hour"
    const val OSCILLATOR_SCHEDULE_MINUTE = "oscillator_schedule_minute"
    const val OSCILLATOR_SCHEDULE_ENABLED = "oscillator_schedule_enabled"
}

data class KrxCredentials(val id: String, val password: String)

data class EtfKeywordFilter(
    val includeKeywords: List<String>,
    val excludeKeywords: List<String>
)

data class EtfScheduleTime(val hour: Int = 0, val minute: Int = 30)

data class OscillatorScheduleTime(val hour: Int = 1, val minute: Int = 0, val enabled: Boolean = false)

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
    "액티브", "반도체", "바이오", "혁신기술", "배당성장", "신재생",
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
        minute = prefs.getInt(PrefsKeys.ETF_SCHEDULE_MINUTE, 30)
    )
}

suspend fun saveEtfScheduleTime(context: Context, schedule: EtfScheduleTime) = withContext(Dispatchers.IO) {
    getEncryptedPrefs(context).edit()
        .putInt(PrefsKeys.ETF_SCHEDULE_HOUR, schedule.hour)
        .putInt(PrefsKeys.ETF_SCHEDULE_MINUTE, schedule.minute)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var kiwoomAppKey by remember { mutableStateOf("") }
    var kiwoomSecretKey by remember { mutableStateOf("") }
    var kiwoomMode by remember { mutableStateOf(InvestmentMode.MOCK) }

    var kisAppKey by remember { mutableStateOf("") }
    var kisAppSecret by remember { mutableStateOf("") }
    var kisMode by remember { mutableStateOf(InvestmentMode.MOCK) }

    var krxId by remember { mutableStateOf("") }
    var krxPassword by remember { mutableStateOf("") }

    var includeKeywords by remember { mutableStateOf(DEFAULT_INCLUDE_KEYWORDS) }
    var excludeKeywords by remember { mutableStateOf(DEFAULT_EXCLUDE_KEYWORDS) }
    var showAddIncludeDialog by remember { mutableStateOf(false) }
    var showAddExcludeDialog by remember { mutableStateOf(false) }

    var scheduleHour by remember { mutableIntStateOf(0) }
    var scheduleMinute by remember { mutableIntStateOf(30) }
    var manualCollectMessage by remember { mutableStateOf<String?>(null) }

    var oscScheduleEnabled by remember { mutableStateOf(false) }
    var oscScheduleHour by remember { mutableIntStateOf(1) }
    var oscScheduleMinute by remember { mutableIntStateOf(0) }
    var oscManualMessage by remember { mutableStateOf<String?>(null) }

    var saveMessage by remember { mutableStateOf<String?>(null) }

    // 초기 로드
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

            val keywordFilter = loadEtfKeywordFilter(context)
            includeKeywords = keywordFilter.includeKeywords
            excludeKeywords = keywordFilter.excludeKeywords

            val schedule = loadEtfScheduleTime(context)
            scheduleHour = schedule.hour
            scheduleMinute = schedule.minute

            val oscSchedule = loadOscillatorScheduleTime(context)
            oscScheduleEnabled = oscSchedule.enabled
            oscScheduleHour = oscSchedule.hour
            oscScheduleMinute = oscSchedule.minute
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            saveMessage = "설정 로드 실패. 다시 입력해주세요."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 설정") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === Kiwoom API ===
            Text("Kiwoom API", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = kiwoomAppKey,
                onValueChange = { kiwoomAppKey = it },
                label = { Text("App Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = kiwoomSecretKey,
                onValueChange = { kiwoomSecretKey = it },
                label = { Text("Secret Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvestmentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = kiwoomMode == mode,
                        onClick = { kiwoomMode = mode },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            HorizontalDivider()

            // === KIS API ===
            Text("KIS API (한국투자증권)", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = kisAppKey,
                onValueChange = { kisAppKey = it },
                label = { Text("App Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = kisAppSecret,
                onValueChange = { kisAppSecret = it },
                label = { Text("App Secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvestmentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = kisMode == mode,
                        onClick = { kisMode = mode },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            HorizontalDivider()

            // === KRX API ===
            Text("KRX 데이터 (ETF분석용)", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = krxId,
                onValueChange = { krxId = it },
                label = { Text("KRX ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = krxPassword,
                onValueChange = { krxPassword = it },
                label = { Text("KRX 비밀번호") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            // === ETF 키워드 필터 ===
            Text("ETF 키워드 필터", style = MaterialTheme.typography.titleMedium)

            Text("포함 키워드", style = MaterialTheme.typography.bodyMedium)
            KeywordChipRow(
                keywords = includeKeywords,
                onRemove = { kw -> includeKeywords = includeKeywords - kw },
                onAdd = { showAddIncludeDialog = true }
            )

            Text("제외 키워드", style = MaterialTheme.typography.bodyMedium)
            KeywordChipRow(
                keywords = excludeKeywords,
                onRemove = { kw -> excludeKeywords = excludeKeywords - kw },
                onAdd = { showAddExcludeDialog = true }
            )

            if (showAddIncludeDialog) {
                AddKeywordDialog(
                    title = "포함 키워드 추가",
                    onDismiss = { showAddIncludeDialog = false },
                    onConfirm = { keyword ->
                        if (keyword.isNotBlank() && keyword !in includeKeywords) {
                            includeKeywords = includeKeywords + keyword.trim()
                        }
                        showAddIncludeDialog = false
                    }
                )
            }

            if (showAddExcludeDialog) {
                AddKeywordDialog(
                    title = "제외 키워드 추가",
                    onDismiss = { showAddExcludeDialog = false },
                    onConfirm = { keyword ->
                        if (keyword.isNotBlank() && keyword !in excludeKeywords) {
                            excludeKeywords = excludeKeywords + keyword.trim()
                        }
                        showAddExcludeDialog = false
                    }
                )
            }

            HorizontalDivider()

            // === ETF 수집 스케줄 ===
            Text("ETF 자동 수집 시간", style = MaterialTheme.typography.titleMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = "%02d".format(scheduleHour),
                    onValueChange = { v ->
                        v.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                            if (it in 0..23) scheduleHour = it
                        }
                    },
                    label = { Text("시") },
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
                Text(":", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = "%02d".format(scheduleMinute),
                    onValueChange = { v ->
                        v.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                            if (it in 0..59) scheduleMinute = it
                        }
                    },
                    label = { Text("분") },
                    singleLine = true,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    "매일 자동 수집",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // === 수동 수집 버튼 ===
            OutlinedButton(
                onClick = {
                    WorkManagerHelper.runEtfUpdateNow(context)
                    manualCollectMessage = "ETF 데이터 수집을 시작합니다."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("지금 ETF 데이터 수집")
            }

            manualCollectMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // === 시장지표 자동 업데이트 ===
            Text("시장지표 자동 업데이트", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("자동 업데이트 활성화", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = oscScheduleEnabled,
                    onCheckedChange = { oscScheduleEnabled = it }
                )
            }

            if (oscScheduleEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = "%02d".format(oscScheduleHour),
                        onValueChange = { v ->
                            v.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                                if (it in 0..23) oscScheduleHour = it
                            }
                        },
                        label = { Text("시") },
                        singleLine = true,
                        modifier = Modifier.width(80.dp)
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = "%02d".format(oscScheduleMinute),
                        onValueChange = { v ->
                            v.filter { it.isDigit() }.take(2).toIntOrNull()?.let {
                                if (it in 0..59) oscScheduleMinute = it
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
                onClick = {
                    WorkManagerHelper.runOscillatorUpdateNow(context)
                    oscManualMessage = "시장지표 업데이트를 시작합니다."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("지금 시장지표 업데이트")
            }

            oscManualMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            HorizontalDivider()

            // === 저장 버튼 ===
            Button(
                onClick = {
                    scope.launch {
                        try {
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
                                    .putString(PrefsKeys.ETF_INCLUDE_KEYWORDS, includeKeywords.joinToString(","))
                                    .putString(PrefsKeys.ETF_EXCLUDE_KEYWORDS, excludeKeywords.joinToString(","))
                                    .putInt(PrefsKeys.ETF_SCHEDULE_HOUR, scheduleHour)
                                    .putInt(PrefsKeys.ETF_SCHEDULE_MINUTE, scheduleMinute)
                                    .putInt(PrefsKeys.OSCILLATOR_SCHEDULE_HOUR, oscScheduleHour)
                                    .putInt(PrefsKeys.OSCILLATOR_SCHEDULE_MINUTE, oscScheduleMinute)
                                    .putBoolean(PrefsKeys.OSCILLATOR_SCHEDULE_ENABLED, oscScheduleEnabled)
                                    .apply()
                            }
                            // Re-schedule with updated time
                            WorkManagerHelper.scheduleEtfUpdate(context, scheduleHour, scheduleMinute)
                            if (oscScheduleEnabled) {
                                WorkManagerHelper.scheduleOscillatorUpdate(context, oscScheduleHour, oscScheduleMinute)
                            } else {
                                WorkManagerHelper.cancelOscillatorUpdate(context)
                            }
                            saveMessage = "저장되었습니다"
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            saveMessage = "저장 실패. 다시 시도해주세요."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
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
