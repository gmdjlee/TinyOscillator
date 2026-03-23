package com.tinyoscillator.presentation.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tinyoscillator.core.database.AppDatabase
import com.tinyoscillator.presentation.common.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BackupTab(db: AppDatabase) {
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

    // Consensus report backup launchers
    val consensusExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                val result = BackupManager.exportConsensusData(context, it, db)
                backupMessage = result.fold(
                    onSuccess = { count -> "리포트 백업 완료 (${count}건)" },
                    onFailure = { e -> "백업 실패: ${e.message}" }
                )
                isProcessing = false
            }
        }
    }

    val consensusImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                val result = BackupManager.importConsensusData(context, it, db)
                backupMessage = result.fold(
                    onSuccess = { msg -> msg },
                    onFailure = { e -> "복원 실패: ${e.message}" }
                )
                isProcessing = false
            }
        }
    }

    // Data export launcher
    val dataExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                backupMessage = "내보내기 준비 중..."
                val result = BackupManager.exportAllDataForAnalysis(
                    context, it, db
                ) { progress -> backupMessage = progress }
                backupMessage = result.fold(
                    onSuccess = { count -> "데이터 내보내기 완료 (총 ${count}건)" },
                    onFailure = { e -> "내보내기 실패: ${e.message}" }
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
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("API 키 백업/복원", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
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
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { apiImportLauncher.launch(arrayOf("application/octet-stream", "application/json")) },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("API 백업 복원")
            }
        }

        // === ETF 데이터 백업 ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("ETF 데이터 백업/복원", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (availableDates.isNotEmpty()) {
                Text(
                    "DB 저장 기간: ${availableDates.last()} ~ ${availableDates.first()} (${availableDates.size}일)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { etfImportLauncher.launch(arrayOf("application/json")) },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ETF 백업 복원")
            }
        }

        // === 포트폴리오 데이터 백업 ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("포트폴리오 백업/복원", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { portfolioExportLauncher.launch("portfolio_backup.json") },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("포트폴리오 내보내기")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { portfolioImportLauncher.launch(arrayOf("application/json")) },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("포트폴리오 가져오기")
            }
        }

        // === 리포트 데이터 백업 ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("리포트 백업/복원", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { consensusExportLauncher.launch("consensus_report_backup.json") },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("리포트 내보내기")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { consensusImportLauncher.launch(arrayOf("application/json")) },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("리포트 가져오기")
            }
        }

        // === 전체 데이터 내보내기 (분석용) ===
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("전체 데이터 내보내기", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "모든 DB 데이터를 TSV 형식으로 내보냅니다. Claude 등 AI 분석에 활용할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val date = java.time.LocalDate.now().format(com.tinyoscillator.core.util.DateFormats.yyyyMMdd)
                    dataExportLauncher.launch("tinyoscillator_data_$date.txt")
                },
                enabled = !isProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("전체 데이터 내보내기 (TSV)")
            }
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
internal fun PasswordInputDialog(
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
