package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyoscillator.core.api.AiApiClient
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.core.api.KisApiClient
import com.tinyoscillator.core.api.KisApiKeyConfig
import com.tinyoscillator.core.api.KiwoomApiClient
import com.tinyoscillator.core.api.KiwoomApiKeyConfig
import com.tinyoscillator.domain.model.AiModelInfo
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.feature.bearsignal.domain.model.GlobalIndexSource
import com.tinyoscillator.presentation.common.CarvedTextField
import com.tinyoscillator.presentation.common.CategoryBadge
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
    customsTradeApiKey: String = "", onCustomsTradeApiKeyChange: (String) -> Unit = {},
    fredApiKey: String = "", onFredApiKeyChange: (String) -> Unit = {},
    bearSignalIndexSource: GlobalIndexSource = GlobalIndexSource.DEFAULT,
    onBearSignalIndexSourceChange: (GlobalIndexSource) -> Unit = {},
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
        // ── 필수 그룹 — 주가·ETF 수집에 필요한 핵심 자격증명 ──
        ApiGroupLabel("필수 — 주가·ETF 수집")

        // === Kiwoom API ===
        ApiKeyValidationCard(
            title = "Kiwoom API",
            filled = kiwoomAppKey.isNotBlank() && kiwoomSecretKey.isNotBlank(),
            appKeyValue = kiwoomAppKey,
            onAppKeyChange = onKiwoomAppKeyChange,
            appKeyLabel = "App Key",
            secretValue = kiwoomSecretKey,
            onSecretChange = onKiwoomSecretKeyChange,
            secretLabel = "Secret Key",
            mode = kiwoomMode,
            onModeChange = onKiwoomModeChange,
            onValidate = { appKey, secret, mode ->
                val config = KiwoomApiKeyConfig(appKey, secret, mode)
                KiwoomApiClient().validateCredentials(config)
            }
        )

        // === KRX API ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("KRX 데이터 (ETF분석용)", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary)
                FilledBadge(krxId.isNotBlank() && krxPassword.isNotBlank())
            }
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

        // ── 선택·고급 그룹 — 미설정 시 해당 분석만 비활성화 ──
        ApiGroupLabel("선택 · 고급")

        // === KIS API ===
        ApiKeyValidationCard(
            title = "KIS API (한국투자증권)",
            filled = kisAppKey.isNotBlank() && kisAppSecret.isNotBlank(),
            appKeyValue = kisAppKey,
            onAppKeyChange = onKisAppKeyChange,
            appKeyLabel = "App Key",
            secretValue = kisAppSecret,
            onSecretChange = onKisAppSecretChange,
            secretLabel = "App Secret",
            mode = kisMode,
            onModeChange = onKisModeChange,
            onValidate = { appKey, secret, mode ->
                val config = KisApiKeyConfig(appKey, secret, mode)
                KisApiClient().validateCredentials(config)
            }
        )

        // === AI API ===
        AiApiSection(
            filled = aiApiKey.isNotBlank(),
            aiApiKey = aiApiKey,
            onAiApiKeyChange = onAiApiKeyChange,
            aiProvider = aiProvider,
            onAiProviderChange = onAiProviderChange,
            aiModelId = aiModelId,
            onAiModelIdChange = onAiModelIdChange
        )

        // === DART OpenAPI ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("DART OpenAPI (공시 분석)", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary)
                FilledBadge(dartApiKey.isNotBlank())
            }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BOK ECOS (매크로 지표)", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary)
                FilledBadge(ecosApiKey.isNotBlank())
            }
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

        // === 관세청 무역통계 Open API (BearSignal 「주도주 붕괴 판단 계기판」) ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("관세청 무역통계 (수출 비중)", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary)
                FilledBadge(customsTradeApiKey.isNotBlank())
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = customsTradeApiKey,
                onValueChange = onCustomsTradeApiKeyChange,
                label = { Text("관세청 API Key") },
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
                    "• data.go.kr(공공데이터포털)에서 관세청_수출입무역통계 인증키 발급\n• 반도체 수출 비중·완충산업 건재 여부 산출\n• 미설정 시 「주도주 붕괴 판단 계기판」 증폭 지표가 비활성화됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // === FRED API (BearSignal 「주도주 붕괴 판단 계기판」) ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("FRED (미 연준 기준금리)", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary)
                FilledBadge(fredApiKey.isNotBlank())
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = fredApiKey,
                onValueChange = onFredApiKeyChange,
                label = { Text("FRED API Key") },
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
                    "• fred.stlouisfed.org에서 무료 API Key 발급\n• 연방기금금리 목표 상단(DFEDTARU) 수집\n• 미설정 시 「주도주 붕괴 판단 계기판」 금리 방아쇠 지표가 비활성화됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        // === 해외지수 시세 소스 (BearSignal 「주도주 붕괴 판단 계기판」) ===
        BearSignalIndexSourceSection(
            selected = bearSignalIndexSource,
            onChange = onBearSignalIndexSourceChange
        )

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

/** 그룹 라벨 — 필수/선택 구분 헤더 (온보딩 필수·선택 구분과 정합) */
@Composable
private fun ApiGroupLabel(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

/** 입력 상태 배지 — 자격증명 입력 여부를 카드 헤더 우측에 표시 */
@Composable
private fun FilledBadge(filled: Boolean) = CategoryBadge(
    text = if (filled) "입력됨" else "미입력",
    color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
)

/** 해외지수·IPO ETF 시세 소스 선택 — 인증키 불필요, 선택 소스 실패 시 나머지 소스로 자동 폴백 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BearSignalIndexSourceSection(
    selected: GlobalIndexSource,
    onChange: (GlobalIndexSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text("해외지수 시세 소스 (계기판)", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selected.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("기본 소스") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                GlobalIndexSource.entries.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(source.displayName) },
                        onClick = {
                            onChange(source)
                            expanded = false
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                "• 「주도주 붕괴 판단 계기판」 해외지수 6종·IPO ETF 시세 수집 소스\n• 인증키 불필요 — 선택한 소스 실패 시 나머지 소스로 자동 폴백\n• Stooq는 봇 차단이 관측되어 Yahoo Finance 권장",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiApiSection(
    filled: Boolean,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI 분석 (Claude / Gemini)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            FilledBadge(filled)
        }
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
                                    modelTierHint(model.id)?.let { hint ->
                                        Text(hint, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
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
            Text(
                "Haiku/Flash: 빠르고 저렴 (일상 분석 권장) · Sonnet/Pro: 심층 분석 (비용 높음)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
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

@Composable
private fun ApiKeyValidationCard(
    title: String,
    filled: Boolean,
    appKeyValue: String, onAppKeyChange: (String) -> Unit, appKeyLabel: String,
    secretValue: String, onSecretChange: (String) -> Unit, secretLabel: String,
    mode: InvestmentMode, onModeChange: (InvestmentMode) -> Unit,
    onValidate: suspend (appKey: String, secret: String, mode: InvestmentMode) -> Result<Unit>
) {
    val scope = rememberCoroutineScope()
    var validateState by remember { mutableStateOf<FetchState>(FetchState.Idle) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            FilledBadge(filled)
        }
        Spacer(Modifier.height(12.dp))
        CarvedTextField(
            value = appKeyValue,
            onValueChange = onAppKeyChange,
            label = appKeyLabel
        )
        Spacer(Modifier.height(12.dp))
        CarvedTextField(
            value = secretValue,
            onValueChange = onSecretChange,
            label = secretLabel,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InvestmentMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    label = { Text(m.displayName) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                if (appKeyValue.isBlank() || secretValue.isBlank()) {
                    validateState = FetchState.Error("App Key와 Secret Key를 입력해주세요")
                    return@OutlinedButton
                }
                validateState = FetchState.Loading
                scope.launch {
                    onValidate(appKeyValue, secretValue, mode).fold(
                        onSuccess = { validateState = FetchState.Success("키 검증 성공") },
                        onFailure = { e -> validateState = FetchState.Error("키 검증 실패: ${e.message}") }
                    )
                }
            },
            enabled = validateState !is FetchState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (validateState is FetchState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("키 검증")
        }
        when (val state = validateState) {
            is FetchState.Success -> Text(
                state.message,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            is FetchState.Error -> Text(
                state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
            else -> {}
        }
    }
}

/** 모델 등급 힌트 — 속도/비용 특성을 한 줄로 안내 */
private fun modelTierHint(modelId: String): String? {
    val id = modelId.lowercase()
    return when {
        id.contains("haiku") || id.contains("flash") -> "빠름 · 저렴 — 일상 분석에 적합"
        id.contains("sonnet") || (id.contains("gemini") && id.contains("pro")) -> "심층 분석 — 비용 높음"
        id.contains("opus") -> "최고 성능 — 비용 매우 높음"
        else -> null
    }
}

private sealed class FetchState {
    data object Idle : FetchState()
    data object Loading : FetchState()
    data class Success(val message: String) : FetchState()
    data class Error(val message: String) : FetchState()
}
