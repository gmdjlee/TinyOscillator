package com.tinyoscillator.presentation.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
                                    .apply()
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
