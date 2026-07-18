package com.tinyoscillator.presentation.portfolio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.PortfolioHoldingItem
import com.tinyoscillator.domain.model.PortfolioSummary
import com.tinyoscillator.domain.model.PortfolioUiState
import com.tinyoscillator.domain.model.TransactionItem
import com.tinyoscillator.presentation.common.CategoryBadge
import com.tinyoscillator.presentation.common.FinanceCard
import com.tinyoscillator.presentation.quickanalysis.StockQuickAnalysisSheet
import com.tinyoscillator.ui.theme.LocalFinanceColors
import java.text.NumberFormat
import java.util.Locale

private val krwFormat = NumberFormat.getNumberInstance(Locale.KOREA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioContent(
    viewModel: PortfolioViewModel,
    onOpenFullAnalysis: (String, String) -> Unit = { _, _ -> },
    onOpenProbabilityAnalysis: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snapshotScores by viewModel.snapshotScores.collectAsStateWithLifecycle()
    val selectedHoldingId by viewModel.selectedHoldingId.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val selectedHoldingName by viewModel.selectedHoldingName.collectAsStateWithLifecycle()
    val selectedHoldingCurrentPrice by viewModel.selectedHoldingCurrentPrice.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showAddTransactionDialog by remember { mutableStateOf(false) }
    var addTransactionHoldingId by remember { mutableLongStateOf(0L) }
    var addTransactionIsSell by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editTargetHolding by remember { mutableStateOf<PortfolioHoldingItem?>(null) }
    var showEditTransactionDialog by remember { mutableStateOf(false) }
    var editTargetTransaction by remember { mutableStateOf<TransactionItem?>(null) }
    var quickAnalysisStock by remember { mutableStateOf<Pair<String, String>?>(null) }

    Box(modifier = modifier) {
        when (val state = uiState) {
            is PortfolioUiState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("포트폴리오를 로딩 중입니다...")
                }
            }

            is PortfolioUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.message)
                    }
                }
            }

            is PortfolioUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is PortfolioUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Summary Card
                    item {
                        SummaryCard(summary = state.summary)
                    }

                    // Pie Chart
                    if (state.holdings.isNotEmpty()) {
                        item {
                            PortfolioPieChart(
                                holdings = state.holdings,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Holdings Table Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "보유종목 (${state.holdings.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // Holdings Card List
                    if (state.holdings.isNotEmpty()) {
                        item {
                            HoldingsCardList(
                                holdings = state.holdings,
                                snapshotScores = snapshotScores,
                                onRowClick = { holding ->
                                    viewModel.selectHolding(
                                        holding.holdingId,
                                        holding.stockName,
                                        holding.currentPrice
                                    )
                                },
                                onQuickAnalysisClick = { holding ->
                                    quickAnalysisStock = holding.ticker to holding.stockName
                                }
                            )
                        }
                    } else {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    "보유 종목이 없습니다.\n아래 + 버튼으로 종목을 추가하세요.",
                                    modifier = Modifier.padding(24.dp),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "종목 추가")
        }
    }

    // Add Holding Dialog
    if (showAddDialog) {
        AddHoldingDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onConfirm = { ticker, stockName, market, sector, shares, price, date, memo, targetPrice ->
                viewModel.addHolding(ticker, stockName, market, sector, shares, price, date, memo, targetPrice)
                showAddDialog = false
            }
        )
    }

    // Quick Analysis Sheet (종목명 셀 탭 진입)
    quickAnalysisStock?.let { (stockTicker, stockName) ->
        StockQuickAnalysisSheet(
            ticker = stockTicker,
            stockName = stockName,
            onDismiss = { quickAnalysisStock = null },
            onOpenFullAnalysis = { t, n ->
                quickAnalysisStock = null
                onOpenFullAnalysis(t, n)
            },
            onOpenProbabilityAnalysis = { t, n ->
                quickAnalysisStock = null
                onOpenProbabilityAnalysis(t, n)
            }
        )
    }

    // Transaction History Sheet
    if (selectedHoldingId != null) {
        TransactionHistorySheet(
            holdingName = selectedHoldingName,
            transactions = transactions,
            onDismiss = { viewModel.clearSelectedHolding() },
            onAddBuy = {
                addTransactionHoldingId = selectedHoldingId!!
                addTransactionIsSell = false
                showAddTransactionDialog = true
            },
            onAddSell = {
                addTransactionHoldingId = selectedHoldingId!!
                addTransactionIsSell = true
                showAddTransactionDialog = true
            },
            onEditHolding = {
                val currentState = uiState
                if (currentState is PortfolioUiState.Success) {
                    editTargetHolding = currentState.holdings.find { it.holdingId == selectedHoldingId }
                    showEditDialog = true
                }
            },
            onEditTransaction = { tx ->
                editTargetTransaction = tx
                showEditTransactionDialog = true
            },
            onDeleteTransaction = { viewModel.deleteTransaction(it) },
            onDeleteHolding = {
                viewModel.deleteHolding(selectedHoldingId!!)
                viewModel.clearSelectedHolding()
            }
        )
    }

    // Add Transaction Dialog
    if (showAddTransactionDialog) {
        AddTransactionDialog(
            isSell = addTransactionIsSell,
            onDismiss = { showAddTransactionDialog = false },
            onConfirm = { shares, price, date, memo ->
                val actualShares = if (addTransactionIsSell) -shares else shares
                viewModel.addTransaction(addTransactionHoldingId, actualShares, price, date, memo)
                showAddTransactionDialog = false
            }
        )
    }

    // Edit Holding Dialog
    if (showEditDialog && editTargetHolding != null) {
        EditHoldingDialog(
            holding = editTargetHolding!!,
            onDismiss = {
                showEditDialog = false
                editTargetHolding = null
            },
            onConfirm = { stockName, market, sector, targetPrice ->
                viewModel.updateHolding(
                    editTargetHolding!!.holdingId,
                    stockName, market, sector, targetPrice
                )
                showEditDialog = false
                editTargetHolding = null
            }
        )
    }

    // Edit Transaction Dialog
    if (showEditTransactionDialog && editTargetTransaction != null) {
        EditTransactionDialog(
            transaction = editTargetTransaction!!,
            onDismiss = {
                showEditTransactionDialog = false
                editTargetTransaction = null
            },
            onConfirm = { shares, pricePerShare, date, memo ->
                viewModel.updateTransaction(
                    editTargetTransaction!!.id,
                    shares, pricePerShare, date, memo
                )
                showEditTransactionDialog = false
                editTargetTransaction = null
            }
        )
    }
}

@Composable
private fun SummaryCard(summary: PortfolioSummary) {
    // 손익 부호 색상 — 한국식(상승=적, 하락=청), 다크 모드 변형은 LocalFinanceColors가 제공
    val gainColor = LocalFinanceColors.current.positive
    val lossColor = LocalFinanceColors.current.negative
    FinanceCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "포트폴리오 요약",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (summary.totalAssets > summary.totalEvaluation) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "총 자산",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${krwFormat.format(summary.totalAssets)}원",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "주식비중",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val stockRatio = if (summary.totalAssets > 0)
                        summary.totalEvaluation.toDouble() / summary.totalAssets * 100.0 else 0.0
                    Text(
                        "${String.format("%.1f", stockRatio)}%",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "총평가금액",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 카드의 시각 앵커 — titleLarge Bold로 승격
                Text(
                    "${krwFormat.format(summary.totalEvaluation)}원",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "총투자금액",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${krwFormat.format(summary.totalInvested)}원",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val plColor = when {
                summary.totalProfitLoss > 0 -> gainColor
                summary.totalProfitLoss < 0 -> lossColor
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column {
                Text(
                    "총수익률",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${if (summary.totalProfitLossPercent >= 0) "+" else ""}${String.format("%.2f", summary.totalProfitLossPercent)}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = plColor
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "총수익금",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${if (summary.totalProfitLoss >= 0) "+" else ""}${krwFormat.format(summary.totalProfitLoss)}원",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = plColor
                )
            }
        }

        if (summary.totalRealizedProfitLoss != 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            val rlColor = when {
                summary.totalRealizedProfitLoss > 0 -> gainColor
                summary.totalRealizedProfitLoss < 0 -> lossColor
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "실현손익",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${if (summary.totalRealizedProfitLoss >= 0) "+" else ""}${krwFormat.format(summary.totalRealizedProfitLoss)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = rlColor
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "보유종목 ${summary.holdingsCount}개",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** rememberSaveable용 Set&lt;Long&gt; 저장기 — 프로세스 재생성 후에도 펼침 상태 복원 */
private val LongSetSaver: Saver<Set<Long>, ArrayList<Long>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toSet() }
)

/**
 * 수익금 축약 표기 — 카드 2행 우측의 좁은 폭에서 원 단위 전체 표기(최장 11자)가 말줄임으로
 * 잘려 금액이 오독되는 것을 방지. 1만 미만 원 단위, 1억 미만 만 단위(반올림), 이상 억 단위.
 * 원 단위 정밀값은 확장 상세 블록의 "수익금(원)" 항목에서 제공.
 */
private fun formatCompactAmount(v: Long): String {
    val absV = kotlin.math.abs(v)
    return when {
        absV < 10_000L -> krwFormat.format(v)
        else -> {
            val man = Math.round(v / 10_000.0)
            if (kotlin.math.abs(man) >= 10_000L) String.format(Locale.KOREA, "%.1f억", v / 100_000_000.0)
            else "${krwFormat.format(man)}만"
        }
    }
}

/**
 * 보유종목 카드 리스트 — 표(5열) 대신 종목별 FinanceCard로 나열.
 * 펼침 상태는 프로세스 재생성에도 복원되도록 rememberSaveable로 이 계층에서 관리.
 */
@Composable
private fun HoldingsCardList(
    holdings: List<PortfolioHoldingItem>,
    snapshotScores: Map<String, Double>,
    onRowClick: (PortfolioHoldingItem) -> Unit,
    onQuickAnalysisClick: (PortfolioHoldingItem) -> Unit
) {
    val gainColor = LocalFinanceColors.current.positive
    val lossColor = LocalFinanceColors.current.negative
    var expandedIds by rememberSaveable(stateSaver = LongSetSaver) { mutableStateOf(emptySet<Long>()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        holdings.forEach { holding ->
            HoldingCard(
                holding = holding,
                score = snapshotScores[holding.ticker],
                isExpanded = holding.holdingId in expandedIds,
                gainColor = gainColor,
                lossColor = lossColor,
                onRowClick = onRowClick,
                onQuickAnalysisClick = onQuickAnalysisClick,
                onToggleExpand = {
                    expandedIds = if (holding.holdingId in expandedIds) {
                        expandedIds - holding.holdingId
                    } else {
                        expandedIds + holding.holdingId
                    }
                }
            )
        }
    }
}

/**
 * 종목 카드 — 카드 탭=거래내역(주 액션), 오버플로(⋯)=거래내역/퀵 분석/상세 토글.
 * 1행: 종목명 + 신호 배지 + 오버플로 / 2행: 현재가 · 수익률·수익금 / 확장: 상세 블록.
 */
@Composable
private fun HoldingCard(
    holding: PortfolioHoldingItem,
    score: Double?,
    isExpanded: Boolean,
    gainColor: Color,
    lossColor: Color,
    onRowClick: (PortfolioHoldingItem) -> Unit,
    onQuickAnalysisClick: (PortfolioHoldingItem) -> Unit,
    onToggleExpand: () -> Unit
) {
    val plColor = when {
        holding.profitLossAmount > 0 -> gainColor
        holding.profitLossAmount < 0 -> lossColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    var menuOpen by remember { mutableStateOf(false) }

    FinanceCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        onClick = { onRowClick(holding) }
    ) {
        // 1행: 종목명 + 신호 배지 + 오버플로 메뉴
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = holding.stockName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (score != null) {
                val badgeColor = when {
                    score >= 0.65 -> gainColor
                    score <= 0.35 -> lossColor
                    else -> MaterialTheme.colorScheme.outline
                }
                CategoryBadge(
                    text = "신호 ${(score * 100).toInt()}%",
                    color = badgeColor
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "종목 메뉴",
                        modifier = Modifier.size(20.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("거래내역") },
                        onClick = {
                            menuOpen = false
                            onRowClick(holding)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("퀵 분석") },
                        onClick = {
                            menuOpen = false
                            onQuickAnalysisClick(holding)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isExpanded) "상세 접기" else "상세 보기") },
                        onClick = {
                            menuOpen = false
                            onToggleExpand()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2행: 현재가 (좌) · 수익률·수익금 (우)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    "현재가",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (holding.currentPrice > 0) "${krwFormat.format(holding.currentPrice)}원" else "-",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // |수익률|≥100%는 소수점을 버려 표기
                    "${if (holding.profitLossPercent >= 0) "+" else ""}${
                        if (kotlin.math.abs(holding.profitLossPercent) >= 100.0) {
                            String.format(Locale.KOREA, "%.0f", holding.profitLossPercent)
                        } else {
                            String.format(Locale.KOREA, "%.1f", holding.profitLossPercent)
                        }
                    }%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = plColor
                )
                Text(
                    "${if (holding.profitLossAmount >= 0) "+" else ""}${formatCompactAmount(holding.profitLossAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = plColor
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            HoldingDetailBlock(holding = holding, gainColor = gainColor, lossColor = lossColor)
        }
    }
}

/** 15열 표에서 핵심 5열로 이동한 나머지 항목 + 수익금 원 단위 정밀값 — 행별 인라인 확장 상세 블록 (2열 라벨-값 그리드) */
@Composable
private fun HoldingDetailBlock(
    holding: PortfolioHoldingItem,
    gainColor: Color,
    lossColor: Color
) {
    val targetReached = holding.targetPrice > 0 && holding.currentPrice >= holding.targetPrice
    val rlColor = when {
        holding.realizedProfitLoss > 0 -> gainColor
        holding.realizedProfitLoss < 0 -> lossColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val plColor = when {
        holding.profitLossAmount > 0 -> gainColor
        holding.profitLossAmount < 0 -> lossColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    val fields = listOf(
        DetailField("시장", holding.market),
        DetailField("업종", holding.sector),
        DetailField("보유수", krwFormat.format(holding.totalShares)),
        DetailField("평균매입가", krwFormat.format(holding.avgBuyPrice)),
        DetailField(
            "목표가",
            if (holding.targetPrice > 0) krwFormat.format(holding.targetPrice) else "-",
            color = if (targetReached) gainColor else null
        ),
        DetailField(
            "비중%",
            "${String.format("%.1f", holding.weightPercent)}%${if (holding.isOverWeight) " 초과" else ""}",
            color = if (holding.isOverWeight) gainColor else null,
            bold = holding.isOverWeight
        ),
        DetailField(
            "조절주식",
            if (holding.rebalanceShares > 0) krwFormat.format(holding.rebalanceShares) else "-"
        ),
        DetailField(
            "조절금액",
            if (holding.rebalanceAmount > 0) krwFormat.format(holding.rebalanceAmount) else "-"
        ),
        DetailField(
            "실현손익",
            if (holding.realizedProfitLoss != 0L)
                "${if (holding.realizedProfitLoss >= 0) "+" else ""}${krwFormat.format(holding.realizedProfitLoss)}"
            else "-",
            color = if (holding.realizedProfitLoss != 0L) rlColor else null
        ),
        // 핵심 열의 수익금은 만/억 축약 표기 — 원 단위 정밀값은 여기서 제공
        DetailField(
            "수익금(원)",
            "${if (holding.profitLossAmount >= 0) "+" else ""}${krwFormat.format(holding.profitLossAmount)}",
            color = plColor
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        fields.chunked(2).forEach { rowFields ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowFields.forEach { field ->
                    DetailFieldCell(field, modifier = Modifier.weight(1f))
                }
                if (rowFields.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class DetailField(
    val label: String,
    val value: String,
    val color: Color? = null,
    val bold: Boolean = false
)

@Composable
private fun DetailFieldCell(field: DetailField, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = field.value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (field.bold) FontWeight.Bold else null,
            color = field.color ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

