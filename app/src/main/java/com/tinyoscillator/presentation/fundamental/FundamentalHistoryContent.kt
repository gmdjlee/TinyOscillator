package com.tinyoscillator.presentation.fundamental

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tinyoscillator.domain.model.FundamentalHistoryState

@Composable
fun FundamentalHistoryContent(
    ticker: String?,
    stockName: String?,
    modifier: Modifier = Modifier,
    viewModel: FundamentalHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(ticker, stockName) {
        if (ticker != null && stockName != null) {
            viewModel.loadForStock(ticker, stockName)
        } else {
            viewModel.clearStock()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        when (val currentState = state) {
            is FundamentalHistoryState.NoStock -> {
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

            is FundamentalHistoryState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "투자지표를 불러오는 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is FundamentalHistoryState.NoKrxLogin -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "KRX 로그인이 필요합니다.\n설정 화면에서 KRX 계정을 입력해주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        TextButton(onClick = { viewModel.retry() }) {
                            Text("다시 시도")
                        }
                    }
                }
            }

            is FundamentalHistoryState.Success -> {
                FundamentalHistoryCharts(
                    data = currentState.data,
                    stockName = currentState.stockName,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is FundamentalHistoryState.Error -> {
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
