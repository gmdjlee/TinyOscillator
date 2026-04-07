package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.domain.model.AiModelInfo
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.presentation.common.CarvedTextField
import com.tinyoscillator.presentation.common.GlassCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApiTab(
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
    aiModelId: String, onAiModelIdChange: (String) -> Unit,
    dartApiKey: String = "", onDartApiKeyChange: (String) -> Unit = {},
    ecosApiKey: String = "", onEcosApiKeyChange: (String) -> Unit = {},
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
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("Kiwoom API", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            CarvedTextField(
                value = kiwoomAppKey,
                onValueChange = onKiwoomAppKeyChange,
                label = "App Key"
            )
            Spacer(Modifier.height(12.dp))
            CarvedTextField(
                value = kiwoomSecretKey,
                onValueChange = onKiwoomSecretKeyChange,
                label = "Secret Key",
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvestmentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = kiwoomMode == mode,
                        onClick = { onKiwoomModeChange(mode) },
                        label = { Text(mode.displayName) }
                    )
                }
            }
        }

        // === KIS API ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("KIS API (한국투자증권)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            CarvedTextField(
                value = kisAppKey,
                onValueChange = onKisAppKeyChange,
                label = "App Key"
            )
            Spacer(Modifier.height(12.dp))
            CarvedTextField(
                value = kisAppSecret,
                onValueChange = onKisAppSecretChange,
                label = "App Secret",
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvestmentMode.entries.forEach { mode ->
                    FilterChip(
                        selected = kisMode == mode,
                        onClick = { onKisModeChange(mode) },
                        label = { Text(mode.displayName) }
                    )
                }
            }
        }

        // === KRX API ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("KRX 데이터 (ETF분석용)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            CarvedTextField(
                value = krxId,
                onValueChange = onKrxIdChange,
                label = "KRX ID"
            )
            Spacer(Modifier.height(12.dp))
            CarvedTextField(
                value = krxPassword,
                onValueChange = onKrxPasswordChange,
                label = "KRX 비밀번호",
                visualTransformation = PasswordVisualTransformation()
            )
        }

        // === AI API ===
        AiApiSection(
            aiApiKey = aiApiKey,
            onAiApiKeyChange = onAiApiKeyChange,
            aiProvider = aiProvider,
            onAiProviderChange = onAiProviderChange,
            aiModelId = aiModelId,
            onAiModelIdChange = onAiModelIdChange
        )

        // === DART OpenAPI ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("DART OpenAPI (공시 분석)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dartApiKey,
                onValueChange = onDartApiKeyChange,
                label = { Text("DART API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "• opendart.fss.or.kr에서 인증키 발급\n• 일 10,000건 제한 — 캐시 자동 적용\n• 미설정 시 공시 이벤트 분석이 비활성화됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // === BOK ECOS API ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("BOK ECOS (매크로 지표)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ecosApiKey,
                onValueChange = onEcosApiKeyChange,
                label = { Text("ECOS API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "• ecos.bok.or.kr에서 인증키 발급\n• 기준금리, M2, 산업생산, 환율, CPI 수집\n• 미설정 시 매크로 환경 분석이 비활성화됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

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
private fun AiApiSection(
    aiApiKey: String,
    onAiApiKeyChange: (String) -> Unit,
    aiProvider: AiProvider,
    onAiProviderChange: (AiProvider) -> Unit,
    aiModelId: String,
    onAiModelIdChange: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var modelList by remember { mutableStateOf<List<AiModelInfo>>(emptyList()) }
    var fetchState by remember { mutableStateOf<FetchState>(FetchState.Idle) }

    // Provider 변경 시 모델 목록 초기화
    LaunchedEffect(aiProvider) {
        modelList = emptyList()
        fetchState = FetchState.Idle
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("AI 분석 (Claude / Gemini)", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))

        // 1) Provider 선택
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
        Spacer(Modifier.height(12.dp))

        // 2) API Key 입력
        OutlinedTextField(
            value = aiApiKey,
            onValueChange = onAiApiKeyChange,
            label = { Text("API Key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        // 3) 모델 목록 불러오기 버튼
        OutlinedButton(
            onClick = {
                if (aiApiKey.isBlank()) {
                    fetchState = FetchState.Error("API Key를 먼저 입력해주세요")
                    return@OutlinedButton
                }
                fetchState = FetchState.Loading
                scope.launch {
                    val result = AiApiClient().fetchModels(aiProvider, aiApiKey)
                    result.fold(
                        onSuccess = { models ->
                            modelList = models
                            fetchState = if (models.isEmpty()) FetchState.Error("사용 가능한 모델이 없습니다")
                            else FetchState.Success("${models.size}개 모델 로드")
                        },
                        onFailure = { e ->
                            fetchState = FetchState.Error("모델 조회 실패: ${e.message}")
                        }
                    )
                }
            },
            enabled = fetchState !is FetchState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (fetchState is FetchState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("모델 목록 불러오기")
        }

        // 상태 메시지
        when (val state = fetchState) {
            is FetchState.Error -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            is FetchState.Success -> Text(
                state.message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            else -> {}
        }

        // 4) 모델 선택 드롭다운
        if (modelList.isNotEmpty() || aiModelId.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            val selectedLabel = modelList.find { it.id == aiModelId }?.displayName
                ?: aiModelId.ifBlank { "모델을 선택해주세요" }

            ExposedDropdownMenuBox(
                expanded = modelExpanded,
                onExpandedChange = { if (modelList.isNotEmpty()) modelExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    modelList.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(model.id, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {
                                onAiModelIdChange(model.id)
                                modelExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                "• Claude: anthropic.com에서 API Key 발급\n• Gemini: aistudio.google.com에서 API Key 발급\n• API Key 입력 후 '모델 목록 불러오기'를 눌러주세요",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

private sealed class FetchState {
    data object Idle : FetchState()
    data object Loading : FetchState()
    data class Success(val message: String) : FetchState()
    data class Error(val message: String) : FetchState()
}
