package com.tinyoscillator.presentation.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.ChatMessage
import com.tinyoscillator.presentation.common.GlassCard

@Composable
internal fun MarketTabContent(
    marketDataPrepared: Boolean,
    marketDataSummary: String,
    marketDataLoading: Boolean,
    chatMessages: List<ChatMessage>,
    chatLoading: Boolean,
    onPrepareData: () -> Unit,
    onSendChat: (String) -> Unit,
    onClearChat: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!marketDataPrepared) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "시장 종합 분석",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "KOSPI/KOSDAQ 과매수·과매도 지표와 투자자 예탁금 동향을 불러온 뒤, AI와 채팅으로 분석을 진행합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onPrepareData,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !marketDataLoading
                ) {
                    if (marketDataLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("데이터 수집 중...")
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("시장 데이터 불러오기")
                    }
                }
            }
        } else {
            ChatSection(
                dataSummary = marketDataSummary,
                chatMessages = chatMessages,
                chatLoading = chatLoading,
                onSendChat = onSendChat,
                onClearChat = onClearChat,
                placeholder = "시장 지표에 대해 질문하세요..."
            )
        }
    }
}
