package com.tinyoscillator.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tinyoscillator.presentation.common.GlassCard

@Composable
internal fun CollectionSettingsTab(
    fearGreedCollectionDays: Int,
    onFearGreedCollectionDaysChange: (Int) -> Unit,
    etfCollectionDays: Int,
    onEtfCollectionDaysChange: (Int) -> Unit,
    marketOscCollectionDays: Int,
    onMarketOscCollectionDaysChange: (Int) -> Unit,
    marketDepositCollectionDays: Int,
    onMarketDepositCollectionDaysChange: (Int) -> Unit,
    consensusCollectionDays: Int,
    onConsensusCollectionDaysChange: (Int) -> Unit,
    saveMessage: String?,
    onSave: () -> Unit,
    onResetData: (String) -> Unit,
    showResetConfirmDialog: String?,
    onShowResetConfirm: (String) -> Unit,
    onDismissResetConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CollectionPeriodCard(
            title = "Fear & Greed 데이터 수집 기간",
            daysBack = fearGreedCollectionDays,
            onDaysBackChange = onFearGreedCollectionDaysChange,
            onSave = onSave,
            resetType = "feargreed",
            onShowResetConfirm = onShowResetConfirm
        )

        CollectionPeriodCard(
            title = "ETF 데이터 수집 기간",
            daysBack = etfCollectionDays,
            onDaysBackChange = onEtfCollectionDaysChange,
            onSave = onSave,
            resetType = "etf",
            onShowResetConfirm = onShowResetConfirm
        )

        CollectionPeriodCard(
            title = "과매수/과매도 데이터 수집 기간",
            daysBack = marketOscCollectionDays,
            onDaysBackChange = onMarketOscCollectionDaysChange,
            onSave = onSave,
            resetType = "oscillator",
            onShowResetConfirm = onShowResetConfirm
        )

        CollectionPeriodCard(
            title = "자금 동향 데이터 수집 기간",
            daysBack = marketDepositCollectionDays,
            onDaysBackChange = onMarketDepositCollectionDaysChange,
            onSave = onSave,
            resetType = "deposit",
            onShowResetConfirm = onShowResetConfirm
        )

        CollectionPeriodCard(
            title = "리포트 데이터 수집 기간",
            daysBack = consensusCollectionDays,
            onDaysBackChange = onConsensusCollectionDaysChange,
            onSave = onSave,
            resetType = "consensus",
            onShowResetConfirm = onShowResetConfirm
        )

        saveMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // 삭제 확인 다이얼로그
    showResetConfirmDialog?.let { dataType ->
        val label = when (dataType) {
            "feargreed" -> "Fear & Greed"
            "etf" -> "ETF"
            "oscillator" -> "과매수/과매도"
            "deposit" -> "자금 동향"
            "consensus" -> "리포트"
            else -> dataType
        }
        AlertDialog(
            onDismissRequest = onDismissResetConfirm,
            title = { Text("$label 데이터 초기화") },
            text = {
                Text("$label 데이터를 모두 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.\n다음 수집 시 설정된 기간만큼 다시 수집됩니다.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onResetData(dataType)
                    onDismissResetConfirm()
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissResetConfirm) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun CollectionPeriodCard(
    title: String,
    daysBack: Int,
    onDaysBackChange: (Int) -> Unit,
    onSave: () -> Unit,
    resetType: String,
    onShowResetConfirm: (String) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        CollectionPeriodRow(
            daysBack = daysBack,
            onDaysBackChange = onDaysBackChange,
            onSave = onSave
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "초기 수집 또는 전체 새로고침 시 수집할 기간입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onShowResetConfirm(resetType) },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("데이터 초기화")
        }
    }
}
