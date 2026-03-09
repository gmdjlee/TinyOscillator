package com.tinyoscillator.presentation.market

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.tinyoscillator.core.worker.MarketDepositUpdateWorker
import com.tinyoscillator.core.worker.MarketOscillatorUpdateWorker
import com.tinyoscillator.presentation.common.CollectionProgressBar

private enum class MarketTab(val label: String) {
    OSCILLATOR("과매수/과매도"),
    DEPOSIT("자금 동향")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketIndicatorScreen(
    onSettingsClick: () -> Unit
) {
    val oscillatorViewModel: MarketOscillatorViewModel = hiltViewModel()
    val depositViewModel: MarketDepositViewModel = hiltViewModel()
    var selectedTab by rememberSaveable { mutableStateOf(MarketTab.OSCILLATOR) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("시장지표") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CollectionProgressBar(
                tag = when (selectedTab) {
                    MarketTab.OSCILLATOR -> MarketOscillatorUpdateWorker.TAG
                    MarketTab.DEPOSIT -> MarketDepositUpdateWorker.TAG
                }
            )

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                MarketTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            when (selectedTab) {
                MarketTab.OSCILLATOR -> MarketOscillatorTab(viewModel = oscillatorViewModel)
                MarketTab.DEPOSIT -> MarketDepositTab(viewModel = depositViewModel)
            }
        }
    }
}
