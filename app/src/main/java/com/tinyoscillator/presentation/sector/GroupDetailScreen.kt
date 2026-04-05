package com.tinyoscillator.presentation.sector

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tinyoscillator.core.database.dao.CalibrationDao
import com.tinyoscillator.core.database.dao.StockMasterDao
import com.tinyoscillator.presentation.common.scoreColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class GroupMemberItem(
    val ticker: String,
    val name: String,
    val signal: Float,
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val masterDao: StockMasterDao,
    private val calibrationDao: CalibrationDao,
) : ViewModel() {

    val groupName: String = savedStateHandle["groupName"] ?: ""
    private val tickersArg: String = savedStateHandle["tickers"] ?: ""

    private val _members = MutableStateFlow<List<GroupMemberItem>>(emptyList())
    val members: StateFlow<List<GroupMemberItem>> = _members

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val tickers = tickersArg.split(",").filter { it.isNotBlank() }
            val scoreMap = calibrationDao.getLatestAvgScoresByTicker()
                .associate { it.ticker to it.avgScore.toFloat() }
            val items = tickers.map { ticker ->
                val name = masterDao.getStockName(ticker) ?: ticker
                GroupMemberItem(
                    ticker = ticker,
                    name = name,
                    signal = scoreMap[ticker] ?: 0.5f,
                )
            }.sortedByDescending { it.signal }
            _members.value = items
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    viewModel: GroupDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onTickerClick: (String) -> Unit = {},
) {
    val members by viewModel.members.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.groupName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(members, key = { it.ticker }) { member ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTickerClick(member.ticker) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                member.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                member.ticker,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${(member.signal * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = scoreColor(member.signal),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
