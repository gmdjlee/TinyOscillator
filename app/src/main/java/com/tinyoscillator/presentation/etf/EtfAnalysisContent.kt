package com.tinyoscillator.presentation.etf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinyoscillator.core.database.entity.EtfEntity
import com.tinyoscillator.core.ui.composable.NeedDataCollectionContent
import com.tinyoscillator.core.ui.composable.NoSearchResultContent
import com.tinyoscillator.presentation.common.CategoryBadge
import com.tinyoscillator.presentation.common.KrxCredentialDialog

@Composable
fun EtfAnalysisContent(
    onEtfClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: EtfViewModel = hiltViewModel()
) {
    val etfList by viewModel.etfList.collectAsStateWithLifecycle()
    val needsCredentials by viewModel.needsCredentials.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }

    val filteredList = remember(etfList, searchQuery) {
        if (searchQuery.isBlank()) etfList
        else etfList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.ticker.contains(searchQuery)
        }
    }

    // KRX Credential Dialog
    if (needsCredentials) {
        KrxCredentialDialog(
            description = "ETF 데이터 수집을 위해 KRX 데이터시스템 계정이 필요합니다.",
            onDismiss = { /* can't dismiss without credentials */ },
            onSave = { viewModel.onCredentialsSaved() }
        )
    }

    Column(modifier = modifier) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("ETF 검색") },
            placeholder = { Text("ETF명 또는 종목코드") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ETF List
        if (filteredList.isEmpty()) {
            if (etfList.isEmpty()) {
                NeedDataCollectionContent()
            } else {
                NoSearchResultContent(query = searchQuery)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.ticker }) { etf ->
                    EtfListItem(
                        etf = etf,
                        onClick = { onEtfClick(etf.ticker) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EtfListItem(
    etf: EtfEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    etf.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        etf.ticker,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    etf.indexName?.let { indexName ->
                        CategoryBadge(text = indexName)
                    }
                }
            }
            etf.totalFee?.let { fee ->
                Text(
                    "%.2f%%".format(fee),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
