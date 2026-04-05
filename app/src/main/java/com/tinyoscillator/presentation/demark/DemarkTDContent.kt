package com.tinyoscillator.presentation.demark

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.DemarkPeriodType

@Composable
fun DemarkTDContent(
    ticker: String?,
    stockName: String?,
    modifier: Modifier = Modifier,
    viewModel: DemarkTDViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()

    LaunchedEffect(ticker, stockName) {
        if (ticker != null && stockName != null) {
            viewModel.loadForStock(ticker, stockName)
        } else {
            viewModel.clearStock()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val currentState = state) {
            is DemarkTDState.NoStock -> {
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

            is DemarkTDState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "DeMark TD 데이터를 불러오는 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is DemarkTDState.Success -> {
                // 일봉/주봉 토글
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DemarkPeriodType.entries.forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = { Text(period.label) }
                        )
                    }
                }

                // 차트
                DemarkTDChart(
                    chartData = currentState.chartData,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            is DemarkTDState.Error -> {
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
                        }
                    }
                }
            }
        }
    }
}
