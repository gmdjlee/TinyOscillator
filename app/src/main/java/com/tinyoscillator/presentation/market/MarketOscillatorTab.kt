package com.tinyoscillator.presentation.market

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.domain.model.DateRangeOption
import com.tinyoscillator.domain.model.MarketOscillator
import com.tinyoscillator.domain.model.MarketOscillatorState
import com.tinyoscillator.domain.model.OscillatorRangeOption
import com.tinyoscillator.core.ui.composable.DefaultErrorContent
import com.tinyoscillator.core.ui.composable.NeedDataCollectionContent
@Composable
fun MarketOscillatorTab(
    viewModel: MarketOscillatorViewModel
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedMarket by viewModel.selectedMarket.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val marketData by viewModel.marketData.collectAsStateWithLifecycle()
    val overboughtThreshold by viewModel.overboughtThreshold.collectAsStateWithLifecycle()
    val oversoldThreshold by viewModel.oversoldThreshold.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        ThresholdSettingsDialog(
            overboughtThreshold = overboughtThreshold,
            oversoldThreshold = oversoldThreshold,
            onDismiss = { showSettingsDialog = false },
            onConfirm = { overbought, oversold ->
                viewModel.onOverboughtThresholdChanged(overbought)
                viewModel.onOversoldThresholdChanged(oversold)
                showSettingsDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Market Selection
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("시장 선택", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showSettingsDialog = true }) {
                        Text("임계값 설정")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedMarket == "KOSPI",
                        onClick = { viewModel.onSelectedMarketChanged("KOSPI") },
                        label = { Text("코스피") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMarket == "KOSDAQ",
                        onClick = { viewModel.onSelectedMarketChanged("KOSDAQ") },
                        label = { Text("코스닥") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Date Range Selector
        OscillatorRangeSelector(
            selectedRange = selectedRange,
            onRangeSelected = { viewModel.updateDateRange(it) }
        )

        // State Display
        when (val currentState = state) {
            is MarketOscillatorState.Loading -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is MarketOscillatorState.Initializing -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Text(currentState.message)
                        Text("${currentState.progress}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            is MarketOscillatorState.Updating -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(currentState.message)
                    }
                }
            }
            is MarketOscillatorState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        currentState.message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    viewModel.clearMessage()
                }
            }
            is MarketOscillatorState.Error -> {
                DefaultErrorContent(
                    message = currentState.message,
                    onRetry = { viewModel.clearMessage() }
                )
            }
            is MarketOscillatorState.Idle -> {
                if (!currentState.hasData || marketData.isEmpty()) {
                    NeedDataCollectionContent()
                }
            }
        }

        // Latest Data Card
        if (marketData.isNotEmpty()) {
            val latest = marketData.firstOrNull()
            if (latest != null) {
                LatestDataCard(latest, overboughtThreshold, oversoldThreshold)
            }
        }

        // Data Table
        if (marketData.isNotEmpty()) {
            DataTable(marketData, overboughtThreshold, oversoldThreshold)
        }
    }
}

@Composable
private fun LatestDataCard(
    latest: MarketOscillator,
    overboughtThreshold: Double,
    oversoldThreshold: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "최신 데이터 (${latest.date})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider()

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("지수", style = MaterialTheme.typography.bodyMedium)
                Text(
                    String.format("%.2f", latest.indexValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Oscillator", style = MaterialTheme.typography.bodyMedium)
                Text(
                    String.format("%.2f%%", latest.oscillator),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        latest.oscillator >= overboughtThreshold -> Color.Red
                        latest.oscillator <= oversoldThreshold -> Color.Blue
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("상태", style = MaterialTheme.typography.bodyMedium)
                val status = when {
                    latest.oscillator >= overboughtThreshold -> "과매수"
                    latest.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        latest.oscillator >= overboughtThreshold -> Color.Red
                        latest.oscillator <= oversoldThreshold -> Color.Blue
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun DataTable(
    data: List<MarketOscillator>,
    overboughtThreshold: Double,
    oversoldThreshold: Double
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "과매수/과매도 내역",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "표시 기간: 최근 ${data.size}일",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("날짜", modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("지수", modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("Oscillator", modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("상태", modifier = Modifier.weight(0.25f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }

            // Rows
            data.forEach { item ->
                val status = when {
                    item.oscillator >= overboughtThreshold -> "과매수"
                    item.oscillator <= oversoldThreshold -> "과매도"
                    else -> "중립"
                }
                val statusColor = when {
                    item.oscillator >= overboughtThreshold -> Color.Red
                    item.oscillator <= oversoldThreshold -> Color.Blue
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.date, modifier = Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text(String.format("%.0f", item.indexValue), modifier = Modifier.weight(0.3f), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, textAlign = TextAlign.End)
                    Text(
                        String.format("%.1f%%", item.oscillator),
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        fontWeight = if (status != "중립") FontWeight.Bold else FontWeight.Normal,
                        color = statusColor,
                        textAlign = TextAlign.End
                    )
                    Text(
                        status,
                        modifier = Modifier.weight(0.25f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )
                }

                if (item != data.last()) {
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun OscillatorRangeSelector(
    selectedRange: OscillatorRangeOption,
    onRangeSelected: (OscillatorRangeOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OscillatorRangeOption.entries.forEach { option ->
            FilterChip(
                selected = selectedRange == option,
                onClick = { onRangeSelected(option) },
                label = { Text(option.label) }
            )
        }
    }
}

@Composable
fun DateRangeSelector(
    selectedRange: DateRangeOption,
    onRangeSelected: (DateRangeOption) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateRangeOption.entries.forEach { option ->
            FilterChip(
                selected = selectedRange == option,
                onClick = { onRangeSelected(option) },
                label = { Text(option.label) }
            )
        }
    }
}

@Composable
private fun ThresholdSettingsDialog(
    overboughtThreshold: Double,
    oversoldThreshold: Double,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var overbought by remember { mutableStateOf(overboughtThreshold.toString()) }
    var oversold by remember { mutableStateOf(oversoldThreshold.toString()) }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("임계값 설정") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("과매수 기준 (%)", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = overbought,
                        onValueChange = { overbought = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("예: 80") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("과매도 기준 (%)", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = oversold,
                        onValueChange = { oversold = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("예: -80") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "• 과매수: Oscillator가 설정값 이상\n• 과매도: Oscillator가 설정값 이하\n• 범위: -100~100",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val overboughtVal = overbought.toDoubleOrNull() ?: overboughtThreshold
                val oversoldVal = oversold.toDoubleOrNull() ?: oversoldThreshold
                onConfirm(overboughtVal, oversoldVal)
            }) { Text("적용") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}
