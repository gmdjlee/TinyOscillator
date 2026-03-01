package com.tinyoscillator.presentation.settings

import android.content.Context
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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "api_settings")

private object PrefsKeys {
    val KIWOOM_APP_KEY = stringPreferencesKey("kiwoom_app_key")
    val KIWOOM_SECRET_KEY = stringPreferencesKey("kiwoom_secret_key")
    val KIWOOM_MODE = stringPreferencesKey("kiwoom_mode")
    val KIS_APP_KEY = stringPreferencesKey("kis_app_key")
    val KIS_APP_SECRET = stringPreferencesKey("kis_app_secret")
    val KIS_MODE = stringPreferencesKey("kis_mode")
}

/**
 * DataStore에서 Kiwoom API 키 설정을 로드합니다.
 */
suspend fun loadKiwoomConfig(context: Context): KiwoomApiKeyConfig {
    val prefs = context.dataStore.data.first()
    return KiwoomApiKeyConfig(
        appKey = prefs[PrefsKeys.KIWOOM_APP_KEY] ?: "",
        secretKey = prefs[PrefsKeys.KIWOOM_SECRET_KEY] ?: "",
        investmentMode = InvestmentMode.entries.find {
            it.name == (prefs[PrefsKeys.KIWOOM_MODE] ?: "MOCK")
        } ?: InvestmentMode.MOCK
    )
}

/**
 * DataStore에서 KIS API 키 설정을 로드합니다.
 */
suspend fun loadKisConfig(context: Context): KisApiKeyConfig {
    val prefs = context.dataStore.data.first()
    return KisApiKeyConfig(
        appKey = prefs[PrefsKeys.KIS_APP_KEY] ?: "",
        appSecret = prefs[PrefsKeys.KIS_APP_SECRET] ?: "",
        investmentMode = InvestmentMode.entries.find {
            it.name == (prefs[PrefsKeys.KIS_MODE] ?: "MOCK")
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
        val kiwoomConfig = loadKiwoomConfig(context)
        kiwoomAppKey = kiwoomConfig.appKey
        kiwoomSecretKey = kiwoomConfig.secretKey
        kiwoomMode = kiwoomConfig.investmentMode

        val kisConfig = loadKisConfig(context)
        kisAppKey = kisConfig.appKey
        kisAppSecret = kisConfig.appSecret
        kisMode = kisConfig.investmentMode
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
                        context.dataStore.edit { prefs ->
                            prefs[PrefsKeys.KIWOOM_APP_KEY] = kiwoomAppKey
                            prefs[PrefsKeys.KIWOOM_SECRET_KEY] = kiwoomSecretKey
                            prefs[PrefsKeys.KIWOOM_MODE] = kiwoomMode.name
                            prefs[PrefsKeys.KIS_APP_KEY] = kisAppKey
                            prefs[PrefsKeys.KIS_APP_SECRET] = kisAppSecret
                            prefs[PrefsKeys.KIS_MODE] = kisMode.name
                        }
                        saveMessage = "저장되었습니다"
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
