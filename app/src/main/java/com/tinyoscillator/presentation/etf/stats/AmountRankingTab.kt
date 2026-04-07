package com.tinyoscillator.presentation.etf.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tinyoscillator.domain.model.AmountRankingItem
import com.tinyoscillator.domain.model.WeightTrend

private enum class SortColumn { AMOUNT, ETF_COUNT, MAX_WEIGHT, AVG_WEIGHT, NEW, INCREASED, DECREASED, REMOVED }
private enum class SortOrder { ASC, DESC }
private data class SortSpec(val column: SortColumn, val order: SortOrder)

/** Weight 레이아웃용 컬럼 비율 (셀 내용 크기 기반) */
private object ColWeights {
    const val RANK = 0.5f        // "100" (3자리)
    const val MARKET = 0.7f      // "코스피" (3글자)
    const val SECTOR = 0.9f      // 업종명 (가변, ellipsis)
    const val STOCK_NAME = 1.8f  // 종목명 (최대 컬럼)
    const val AMOUNT = 0.8f      // "1,234.5"
    const val ETF_COUNT = 0.7f   // 정수
    const val MAX_WEIGHT = 0.9f  // "12.34%▲"
    const val AVG_WEIGHT = 0.8f  // "12.34%"
    const val NEW = 0.6f         // 카운트 배지
    const val INCREASED = 0.6f
    const val DECREASED = 0.6f
    const val REMOVED = 0.6f
}

/** Compact(폰) 모드용 고정 컬럼 폭 */
private object CompactWidths {
    val RANK = 36.dp
    val MARKET = 48.dp
    val SECTOR = 72.dp
    val STOCK_NAME = 150.dp
    val AMOUNT = 64.dp
    val ETF_COUNT = 64.dp
    val MAX_WEIGHT = 72.dp
    val AVG_WEIGHT = 64.dp
    val NEW = 64.dp
    val INCREASED = 64.dp
    val DECREASED = 64.dp
    val REMOVED = 64.dp
}

/** Weight 레이아웃 전환 임계값: 실제 가용 폭 기준 */
private val WEIGHT_LAYOUT_THRESHOLD = 520.dp

private fun encodeSortSpecs(specs: List<SortSpec>): String =
    specs.joinToString(",") { "${it.column.name}:${it.order.name}" }

private fun decodeSortSpecs(encoded: String): List<SortSpec> {
    if (encoded.isEmpty()) return emptyList()
    return encoded.split(",").map { s ->
        val (col, ord) = s.split(":")
        SortSpec(SortColumn.valueOf(col), SortOrder.valueOf(ord))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountRankingTab(
    items: List<AmountRankingItem>,
    sortEncoded: String,
    onSortChange: (String) -> Unit,
    selectedMarket: String?,
    selectedSector: String?,
    availableSectors: List<String>,
    selectedWeightTrend: WeightTrend?,
    onMarketFilter: (String?) -> Unit,
    onSectorFilter: (String?) -> Unit,
    onWeightTrendFilter: (WeightTrend?) -> Unit,
    onStockClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "데이터가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 다중 정렬: 클릭 순서대로 우선순위 부여, 3-state 순환 (중립→DESC→ASC→중립)
    val sortSpecs by remember(sortEncoded) { derivedStateOf { decodeSortSpecs(sortEncoded) } }

    val onHeaderClick = { column: SortColumn ->
        val existing = sortSpecs.find { it.column == column }
        val newSpecs = when {
            existing == null -> sortSpecs + SortSpec(column, SortOrder.DESC)
            existing.order == SortOrder.DESC -> sortSpecs.map {
                if (it.column == column) it.copy(order = SortOrder.ASC) else it
            }
            else -> sortSpecs.filter { it.column != column } // ASC → 중립(제거)
        }
        onSortChange(encodeSortSpecs(newSpecs))
    }

    val sortedItems = remember(items, sortSpecs) {
        if (sortSpecs.isEmpty()) return@remember items
        val comparator = sortSpecs
            .map { spec ->
                val base = when (spec.column) {
                    SortColumn.AMOUNT -> compareBy<AmountRankingItem> { it.totalAmountBillion }
                    SortColumn.ETF_COUNT -> compareBy { it.etfCount }
                    SortColumn.MAX_WEIGHT -> compareBy { it.maxWeight ?: 0.0 }
                    SortColumn.AVG_WEIGHT -> compareBy { it.avgWeight ?: 0.0 }
                    SortColumn.NEW -> compareBy { it.newCount }
                    SortColumn.INCREASED -> compareBy { it.increasedCount }
                    SortColumn.DECREASED -> compareBy { it.decreasedCount }
                    SortColumn.REMOVED -> compareBy { it.removedCount }
                }
                if (spec.order == SortOrder.DESC) base.reversed() else base
            }
            .reduce { acc, c -> acc.then(c) }
        items.sortedWith(comparator)
    }

    // BoxWithConstraints로 실제 가용 폭 측정 (NavigationRail 등 제외)
    BoxWithConstraints(modifier = modifier) {
        val useWeightLayout = maxWidth >= WEIGHT_LAYOUT_THRESHOLD

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Filter row
            item {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    // Market filter chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(null to "전체", "KOSPI" to "코스피", "KOSDAQ" to "코스닥").forEach { (value, label) ->
                            FilterChip(
                                selected = selectedMarket == value,
                                onClick = { onMarketFilter(value) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Sector dropdown
                        var sectorExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = sectorExpanded,
                            onExpandedChange = { sectorExpanded = it }
                        ) {
                            FilterChip(
                                selected = selectedSector != null,
                                onClick = { sectorExpanded = true },
                                label = {
                                    Text(
                                        selectedSector ?: "업종 전체",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = sectorExpanded,
                                onDismissRequest = { sectorExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("전체") },
                                    onClick = {
                                        onSectorFilter(null)
                                        sectorExpanded = false
                                    }
                                )
                                availableSectors.forEach { sector ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                sector,
                                                color = if (sector == selectedSector) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            onSectorFilter(sector)
                                            sectorExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Weight trend dropdown
                        var trendExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = trendExpanded,
                            onExpandedChange = { trendExpanded = it }
                        ) {
                            FilterChip(
                                selected = selectedWeightTrend != null,
                                onClick = { trendExpanded = true },
                                label = {
                                    Text(
                                        when (selectedWeightTrend) {
                                            WeightTrend.UP -> "비중상승"
                                            WeightTrend.DOWN -> "비중감소"
                                            WeightTrend.FLAT -> "비중유지"
                                            WeightTrend.NONE -> "비중없음"
                                            null -> "비중추이"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = trendExpanded,
                                onDismissRequest = { trendExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("전체") },
                                    onClick = {
                                        onWeightTrendFilter(null)
                                        trendExpanded = false
                                    }
                                )
                                listOf(
                                    WeightTrend.UP to "비중상승",
                                    WeightTrend.FLAT to "비중유지",
                                    WeightTrend.DOWN to "비중감소",
                                    WeightTrend.NONE to "비중없음"
                                ).forEach { (trend, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                label,
                                                color = if (trend == selectedWeightTrend) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            onWeightTrendFilter(trend)
                                            trendExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Header
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (!useWeightLayout) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        HeaderCell("#", colModifier(useWeightLayout, ColWeights.RANK, CompactWidths.RANK))
                        HeaderCell("시장", colModifier(useWeightLayout, ColWeights.MARKET, CompactWidths.MARKET))
                        HeaderCell("업종", colModifier(useWeightLayout, ColWeights.SECTOR, CompactWidths.SECTOR))
                        HeaderCell("종목명", colModifier(useWeightLayout, ColWeights.STOCK_NAME, CompactWidths.STOCK_NAME))
                        SortableHeaderCell("금액(억)", colModifier(useWeightLayout, ColWeights.AMOUNT, CompactWidths.AMOUNT), sortSpecs, SortColumn.AMOUNT) { onHeaderClick(SortColumn.AMOUNT) }
                        SortableHeaderCell("ETF수", colModifier(useWeightLayout, ColWeights.ETF_COUNT, CompactWidths.ETF_COUNT), sortSpecs, SortColumn.ETF_COUNT) { onHeaderClick(SortColumn.ETF_COUNT) }
                        SortableHeaderCell("최대비중", colModifier(useWeightLayout, ColWeights.MAX_WEIGHT, CompactWidths.MAX_WEIGHT), sortSpecs, SortColumn.MAX_WEIGHT) { onHeaderClick(SortColumn.MAX_WEIGHT) }
                        SortableHeaderCell("평균비중", colModifier(useWeightLayout, ColWeights.AVG_WEIGHT, CompactWidths.AVG_WEIGHT), sortSpecs, SortColumn.AVG_WEIGHT) { onHeaderClick(SortColumn.AVG_WEIGHT) }
                        SortableHeaderCell("신규", colModifier(useWeightLayout, ColWeights.NEW, CompactWidths.NEW), sortSpecs, SortColumn.NEW) { onHeaderClick(SortColumn.NEW) }
                        SortableHeaderCell("증가", colModifier(useWeightLayout, ColWeights.INCREASED, CompactWidths.INCREASED), sortSpecs, SortColumn.INCREASED) { onHeaderClick(SortColumn.INCREASED) }
                        SortableHeaderCell("감소", colModifier(useWeightLayout, ColWeights.DECREASED, CompactWidths.DECREASED), sortSpecs, SortColumn.DECREASED) { onHeaderClick(SortColumn.DECREASED) }
                        SortableHeaderCell("제외", colModifier(useWeightLayout, ColWeights.REMOVED, CompactWidths.REMOVED), sortSpecs, SortColumn.REMOVED) { onHeaderClick(SortColumn.REMOVED) }
                        if (!useWeightLayout && sortSpecs.isNotEmpty()) {
                            Text(
                                "↺",
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable { onSortChange("") },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    // Weight 레이아웃: 정렬 초기화 버튼을 Row 외부 overlay로 배치
                    if (useWeightLayout && sortSpecs.isNotEmpty()) {
                        Text(
                            "↺",
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp)
                                .clickable { onSortChange("") },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider()
            }

            items(sortedItems, key = { it.stockTicker }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStockClick(item.stockTicker) }
                        .then(if (!useWeightLayout) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "${item.rank}",
                        modifier = colModifier(useWeightLayout, ColWeights.RANK, CompactWidths.RANK),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    MarketLabel(item.market, colModifier(useWeightLayout, ColWeights.MARKET, CompactWidths.MARKET))
                    SectorLabel(item.sector, colModifier(useWeightLayout, ColWeights.SECTOR, CompactWidths.SECTOR))
                    Text(
                        item.stockName,
                        modifier = colModifier(useWeightLayout, ColWeights.STOCK_NAME, CompactWidths.STOCK_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "%.1f".format(item.totalAmountBillion),
                        modifier = colModifier(useWeightLayout, ColWeights.AMOUNT, CompactWidths.AMOUNT),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End
                    )
                    Text(
                        "${item.etfCount}",
                        modifier = colModifier(useWeightLayout, ColWeights.ETF_COUNT, CompactWidths.ETF_COUNT),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    WeightCell(item.maxWeight, item.maxWeightTrend, colModifier(useWeightLayout, ColWeights.MAX_WEIGHT, CompactWidths.MAX_WEIGHT))
                    WeightCell(item.avgWeight, item.avgWeightTrend, colModifier(useWeightLayout, ColWeights.AVG_WEIGHT, CompactWidths.AVG_WEIGHT))
                    CountBadge(item.newCount, MaterialTheme.colorScheme.primary, colModifier(useWeightLayout, ColWeights.NEW, CompactWidths.NEW))
                    CountBadge(item.increasedCount, MaterialTheme.colorScheme.tertiary, colModifier(useWeightLayout, ColWeights.INCREASED, CompactWidths.INCREASED))
                    CountBadge(item.decreasedCount, MaterialTheme.colorScheme.error, colModifier(useWeightLayout, ColWeights.DECREASED, CompactWidths.DECREASED))
                    CountBadge(item.removedCount, MaterialTheme.colorScheme.outline, colModifier(useWeightLayout, ColWeights.REMOVED, CompactWidths.REMOVED))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}

/** RowScope 내에서 화면 크기에 따라 weight 또는 고정 폭 Modifier 반환 */
@Composable
private fun RowScope.colModifier(useWeight: Boolean, weight: Float, fixedWidth: Dp): Modifier =
    if (useWeight) Modifier.weight(weight) else Modifier.width(fixedWidth)

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SortableHeaderCell(
    text: String,
    modifier: Modifier,
    sortSpecs: List<SortSpec>,
    column: SortColumn,
    onClick: () -> Unit
) {
    val spec = sortSpecs.find { it.column == column }
    val priority = if (spec != null) sortSpecs.indexOf(spec) + 1 else 0
    val arrow = when (spec?.order) {
        SortOrder.DESC -> "▼"
        SortOrder.ASC -> "▲"
        null -> ""
    }
    // 다중 정렬 시 우선순위 번호 표시 (2개 이상 선택된 경우만)
    val priorityLabel = if (priority > 0 && sortSpecs.size > 1) "$priority" else ""
    val label = if (spec != null) "$text$priorityLabel$arrow" else text
    Text(
        label,
        modifier = modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (spec != null) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun WeightCell(
    maxWeight: Double?,
    trend: WeightTrend,
    modifier: Modifier
) {
    if (maxWeight != null) {
        val trendSymbol = when (trend) {
            WeightTrend.UP -> "▲"
            WeightTrend.DOWN -> "▼"
            WeightTrend.FLAT -> "-"
            WeightTrend.NONE -> ""
        }
        val trendColor = when (trend) {
            WeightTrend.UP -> Color.Red
            WeightTrend.DOWN -> Color.Blue
            WeightTrend.FLAT -> Color.Black
            WeightTrend.NONE -> Color.Black
        }
        Text(
            "${"%.2f".format(maxWeight)}%$trendSymbol",
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            color = trendColor
        )
    } else {
        Spacer(modifier = modifier)
    }
}

@Composable
private fun CountBadge(
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier
) {
    if (count > 0) {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraSmall,
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                "$count",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    } else {
        Spacer(modifier = modifier)
    }
}
