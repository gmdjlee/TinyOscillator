package com.tinyoscillator.presentation.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.data.local.llm.CachedModel
import com.tinyoscillator.data.local.llm.DownloadProgress
import com.tinyoscillator.data.local.llm.ModelInfo
import com.tinyoscillator.data.local.llm.ModelManager
import kotlinx.coroutines.launch

/**
 * 모델 다운로드 / 관리 화면
 *
 * - 디바이스 RAM 표시 + 추천 모델 하이라이트
 * - 다운로드 진행률 표시
 * - 캐시된 모델 목록 + 삭제
 * - 모델 선택 → 로드
 */
@Composable
fun ModelDownloadScreen(
    modelManager: ModelManager,
    onModelSelected: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val deviceRamGb = remember { modelManager.getDeviceRamGb() }
    val recommendedModel = remember { modelManager.getRecommendedModel() }

    var cachedModels by remember { mutableStateOf<List<CachedModel>>(emptyList()) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }

    LaunchedEffect(Unit) {
        cachedModels = modelManager.getCachedModels()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 디바이스 정보
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("디바이스 RAM: ${deviceRamGb}GB",
                            style = MaterialTheme.typography.titleSmall)
                        Text("추천 모델: ${recommendedModel.name}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // 다운로드 진행률
        if (downloadProgress != null) {
            item {
                DownloadProgressCard(downloadProgress!!)
            }
        }

        // 사용 가능한 모델 목록
        item {
            Text("사용 가능한 모델", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
        }

        items(modelManager.recommendedModels) { model ->
            val isDownloaded = modelManager.isModelDownloaded(model)
            val isRecommended = model.name == recommendedModel.name

            ModelCard(
                model = model,
                isDownloaded = isDownloaded,
                isRecommended = isRecommended,
                onDownload = {
                    scope.launch {
                        modelManager.downloadModel(model).collect { progress ->
                            downloadProgress = progress
                            if (progress is DownloadProgress.Completed ||
                                progress is DownloadProgress.Error) {
                                cachedModels = modelManager.getCachedModels()
                            }
                        }
                    }
                },
                onSelect = {
                    val file = modelManager.getModelFile(model)
                    if (file.exists()) {
                        onModelSelected(file.absolutePath)
                    }
                },
                onDelete = {
                    scope.launch {
                        modelManager.deleteModel(model)
                        cachedModels = modelManager.getCachedModels()
                    }
                }
            )
        }

        // 캐시된 모델
        if (cachedModels.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text("다운로드된 모델", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }

            items(cachedModels) { cached ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cached.modelName, style = MaterialTheme.typography.bodyMedium)
                            Text("${cached.sizeBytes / (1024 * 1024)}MB",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { onModelSelected(cached.filePath) }) {
                            Text("로드")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isDownloaded: Boolean,
    isRecommended: Boolean,
    onDownload: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isRecommended)
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(model.name, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        if (isRecommended) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text("추천", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Text(model.description, style = MaterialTheme.typography.bodySmall)
                    Text("크기: ${model.sizeBytes / (1024 * 1024)}MB | 최소 RAM: ${model.minRamGb}GB",
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDownloaded) {
                    Button(onClick = onSelect) { Text("로드") }
                    OutlinedButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("삭제")
                    }
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = model.url.isNotBlank()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (model.url.isBlank()) "URL 미설정" else "다운로드")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadProgressCard(progress: DownloadProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (progress) {
                is DownloadProgress.Started ->
                    Text("${progress.modelName} 다운로드 시작...")
                is DownloadProgress.Downloading -> {
                    val mb = progress.downloadedBytes / (1024 * 1024)
                    val totalMb = progress.totalBytes / (1024 * 1024)
                    Text("다운로드 중: ${mb}MB / ${totalMb}MB")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is DownloadProgress.Completed ->
                    Text("다운로드 완료!", color = MaterialTheme.colorScheme.primary)
                is DownloadProgress.Error ->
                    Text("오류: ${progress.message}", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
