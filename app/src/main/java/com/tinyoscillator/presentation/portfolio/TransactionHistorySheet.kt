package com.tinyoscillator.presentation.portfolio

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.TransactionItem
import java.text.NumberFormat
import java.util.Locale

private val krwFormat = NumberFormat.getNumberInstance(Locale.KOREA)
private val gainColor = Color(0xFFD32F2F)
private val lossColor = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionHistorySheet(
    holdingName: String,
    transactions: List<TransactionItem>,
    onDismiss: () -> Unit,
    onAddBuy: () -> Unit,
    onAddSell: () -> Unit,
    onEditHolding: () -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onDeleteHolding: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableLongStateOf(0L) }
    var showDeleteHoldingConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                "$holdingName 거래내역",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAddBuy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("매수 추가")
                }
                OutlinedButton(
                    onClick = onAddSell,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("매도 추가")
                }
                IconButton(onClick = onEditHolding) {
                    Icon(Icons.Default.Edit, contentDescription = "종목 수정")
                }
                IconButton(
                    onClick = { showDeleteHoldingConfirm = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "종목 삭제")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            if (transactions.isEmpty()) {
                Text(
                    "거래 내역이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(transactions, key = { it.id }) { tx ->
                        TransactionRow(
                            transaction = tx,
                            onClick = { onEditTransaction(tx) },
                            onLongClick = {
                                deleteTargetId = tx.id
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete transaction confirm
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("거래 삭제") },
            text = { Text("이 거래를 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTransaction(deleteTargetId)
                    showDeleteConfirm = false
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }

    // Delete holding confirm
    if (showDeleteHoldingConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteHoldingConfirm = false },
            title = { Text("종목 삭제") },
            text = { Text("${holdingName}과(와) 모든 거래 내역을 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteHolding()
                    showDeleteHoldingConfirm = false
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteHoldingConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    transaction: TransactionItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isBuy = transaction.shares > 0
    val typeColor = if (isBuy) gainColor else lossColor
    val typeLabel = if (isBuy) "매수" else "매도"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Badge(containerColor = typeColor) {
                        Text(
                            typeLabel,
                            modifier = Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        formatDate(transaction.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${kotlin.math.abs(transaction.shares)}주 × ${krwFormat.format(transaction.pricePerShare)}원",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (transaction.memo.isNotBlank()) {
                    Text(
                        transaction.memo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (transaction.currentPrice > 0 || !isBuy) {
                val plColor = when {
                    transaction.profitLossAmount > 0 -> gainColor
                    transaction.profitLossAmount < 0 -> lossColor
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val plLabel = if (isBuy) "" else "실현 "
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${if (transaction.profitLossPercent >= 0) "+" else ""}${String.format("%.1f", transaction.profitLossPercent)}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = plColor
                    )
                    Text(
                        "${plLabel}${if (transaction.profitLossAmount >= 0) "+" else ""}${krwFormat.format(transaction.profitLossAmount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = plColor
                    )
                }
            }
        }
    }
}

private fun formatDate(date: String): String {
    if (date.length != 8) return date
    return "${date.substring(0, 4)}.${date.substring(4, 6)}.${date.substring(6, 8)}"
}
