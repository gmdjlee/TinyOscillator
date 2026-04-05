package com.tinyoscillator.presentation.sector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.GroupType
import com.tinyoscillator.domain.model.StockGroup
import com.tinyoscillator.presentation.common.scoreColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectorGroupScreen(
    viewModel: SectorGroupViewModel = hiltViewModel(),
    onGroupClick: (StockGroup) -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val krxSectors by viewModel.krxSectors.collectAsStateWithLifecycle()
    val userThemes by viewModel.userThemes.collectAsStateWithLifecycle()
    var showAddTheme by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("섹터/테마") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 내 테마 헤더
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "내 테마",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showAddTheme = true }) {
                        Icon(Icons.Default.Add, "테마 추가")
                    }
                }
            }

            if (userThemes.isEmpty()) {
                item {
                    Text(
                        "테마를 추가해 보세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            items(userThemes, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    onClick = { onGroupClick(group) },
                    onDelete = { viewModel.deleteTheme(group.id) },
                )
            }

            // KRX 섹터 헤더
            item {
                Spacer(Modifier.height(8.dp))
                Text("KRX 섹터", style = MaterialTheme.typography.titleMedium)
            }

            if (krxSectors.isEmpty()) {
                item {
                    Text(
                        "종목 DB를 먼저 갱신해 주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            items(krxSectors, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    onClick = { onGroupClick(group) },
                )
            }
        }

        if (showAddTheme) {
            AddThemeDialog(
                onConfirm = { name, tickers ->
                    viewModel.addTheme(name, tickers)
                    showAddTheme = false
                },
                onDismiss = { showAddTheme = false },
            )
        }
    }
}

@Composable
fun GroupCard(
    group: StockGroup,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    buildString {
                        append("${group.memberCount}종목")
                        if (group.topSignalTicker.isNotEmpty()) {
                            append(" \u00B7 최고: ${group.topSignalTicker}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 평균 신호 게이지
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${(group.avgSignal * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = scoreColor(group.avgSignal),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "평균 신호",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        "삭제",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
