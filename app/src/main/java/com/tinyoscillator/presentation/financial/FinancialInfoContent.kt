package com.tinyoscillator.presentation.financial

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.FinancialState
import com.tinyoscillator.domain.model.FinancialTab

@Composable
fun FinancialInfoContent(
    ticker: String?,
    stockName: String?,
    modifier: Modifier = Modifier,
    viewModel: FinancialInfoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(ticker, stockName) {
        if (ticker != null && stockName != null) {
            viewModel.loadForStock(ticker, stockName)
        } else {
            viewModel.clearStock()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val currentState = state) {
            is FinancialState.NoStock -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "종목을 선택해주세요.\n검색 화면에서 종목을 검색하고 선택하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is FinancialState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "재무정보를 불러오는 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is FinancialState.NoApiKey -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "API 키가 설정되지 않았습니다.\n설정 화면에서 KIS API 키를 입력해주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is FinancialState.Success -> {
                // Refresh button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "새로고침",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Sub-tabs
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FinancialTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { Text(tab.label) }
                        )
                    }
                }

                // Content
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        FinancialTab.PROFITABILITY -> ProfitabilityContent(
                            summary = currentState.summary,
                            modifier = Modifier.fillMaxSize()
                        )
                        FinancialTab.STABILITY -> StabilityContent(
                            summary = currentState.summary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        )
                    }
                }
            }

            is FinancialState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "[ERROR]",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = currentState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = { viewModel.retry() }) {
                                Text("다시 시도")
                            }
                        }
                    }
                }
            }
        }
    }
}
