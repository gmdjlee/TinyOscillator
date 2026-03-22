package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.api.InvestmentMode
import com.tinyoscillator.domain.model.AiProvider
import com.tinyoscillator.presentation.common.CarvedTextField
import com.tinyoscillator.presentation.common.GlassCard

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
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("AI 분석 (Claude / Gemini)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = onAiApiKeyChange,
                label = { Text("API Key") },
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
                    "• Claude: anthropic.com에서 API Key 발급\n• Gemini: aistudio.google.com에서 API Key 발급\n• 권장: Claude Haiku (가장 저렴, 월 ~₩1,100)",
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
