package com.tinyoscillator.presentation.etf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.database.entity.EtfHoldingEntity
import com.tinyoscillator.data.repository.EtfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class EtfDetailViewModel @Inject constructor(
    private val etfRepository: EtfRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private var currentTicker: String = savedStateHandle["ticker"] ?: ""

    private val _etf = MutableStateFlow<EtfEntity?>(null)
    val etf: StateFlow<EtfEntity?> = _etf.asStateFlow()

    private val _holdings = MutableStateFlow<List<EtfHoldingEntity>>(emptyList())
    val holdings: StateFlow<List<EtfHoldingEntity>> = _holdings.asStateFlow()

    private val _dates = MutableStateFlow<List<String>>(emptyList())
    val dates: StateFlow<List<String>> = _dates.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    init {
        if (currentTicker.isNotEmpty()) loadData()
    }

    fun loadForTicker(ticker: String) {
        if (ticker == currentTicker) return
        currentTicker = ticker
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _etf.value = etfRepository.getEtf(currentTicker)
            _dates.value = etfRepository.getAllDates()
            val latestDate = etfRepository.getLatestDate()
            if (latestDate != null) {
                _selectedDate.value = latestDate
                _holdings.value = etfRepository.getHoldings(currentTicker, latestDate)
            }
        }
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        viewModelScope.launch {
            _holdings.value = etfRepository.getHoldings(currentTicker, date)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EtfDetailScreen(
    ticker: String,
    onBack: () -> Unit,
    onStockTrendClick: (String, String) -> Unit = { _, _ -> },
    viewModel: EtfDetailViewModel = hiltViewModel()
) {
    val etf by viewModel.etf.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(etf?.name ?: ticker) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        EtfDetailContent(
            ticker = ticker,
            onStockTrendClick = onStockTrendClick,
            modifier = Modifier.padding(padding),
            viewModel = viewModel
        )
    }
}

@Composable
fun EtfDetailContent(
    ticker: String,
    onStockTrendClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: EtfDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(ticker) {
        viewModel.loadForTicker(ticker)
    }

    val etf by viewModel.etf.collectAsStateWithLifecycle()
    val holdings by viewModel.holdings.collectAsStateWithLifecycle()
    val dates by viewModel.dates.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // ETF Info header
        etf?.let { etfInfo ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(etfInfo.ticker, style = MaterialTheme.typography.bodyMedium)
                        etfInfo.totalFee?.let { fee ->
                            Text(
                                "총보수 %.2f%%".format(fee),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    etfInfo.indexName?.let { idx ->
                        Text(
                            "기초지수: $idx",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Date selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "구성종목 (${holdings.size}개)",
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(onClick = { showDatePicker = true }) {
                Text(selectedDate?.let { formatDate(it) } ?: "날짜 선택")
            }
        }

        if (showDatePicker && dates.isNotEmpty()) {
            DateSelectorDialog(
                dates = dates,
                selectedDate = selectedDate,
                onSelect = {
                    viewModel.selectDate(it)
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Holdings list
        if (holdings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "구성종목 데이터가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(holdings, key = { "${it.etfTicker}_${it.stockTicker}_${it.date}" }) { holding ->
                    HoldingItem(
                        holding = holding,
                        numberFormat = numberFormat,
                        onClick = { onStockTrendClick(ticker, holding.stockTicker) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingItem(
    holding: EtfHoldingEntity,
    numberFormat: NumberFormat,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                holding.stockName,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                holding.stockTicker,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            holding.weight?.let { w ->
                Text(
                    "%.2f%%".format(w),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "${numberFormat.format(holding.amount)}원",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DateSelectorDialog(
    dates: List<String>,
    selectedDate: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("날짜 선택") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(dates, key = { it }) { date ->
                    TextButton(
                        onClick = { onSelect(date) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            formatDate(date),
                            color = if (date == selectedDate) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}

private fun formatDate(yyyymmdd: String): String {
    if (yyyymmdd.length != 8) return yyyymmdd
    return "${yyyymmdd.substring(0, 4)}-${yyyymmdd.substring(4, 6)}-${yyyymmdd.substring(6, 8)}"
}
