package com.tinyoscillator.presentation.portfolio

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onSettingsClick: () -> Unit
) {
    val viewModel: PortfolioViewModel = hiltViewModel()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("포트폴리오") },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshPrices() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "가격 새로고침")
                        }
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "포트폴리오 설정")
                    }
                }
            )
        }
    ) { padding ->
        PortfolioContent(
            viewModel = viewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    if (showSettingsDialog) {
        val portfolio by viewModel.portfolio.collectAsState()
        portfolio?.let {
            PortfolioSettingsDialog(
                name = it.name,
                maxWeightPercent = it.maxWeightPercent,
                totalAmountLimit = it.totalAmountLimit,
                onDismiss = { showSettingsDialog = false },
                onConfirm = { name, maxWeight, limit ->
                    viewModel.updatePortfolioSettings(name, maxWeight, limit)
                    showSettingsDialog = false
                }
            )
        }
    }
}
