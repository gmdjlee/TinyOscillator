package com.tinyoscillator.presentation.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinyoscillator.domain.model.AlgoResult

enum class ContributionChartType { RADAR, WATERFALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlgoContributionView(
    algoResults: Map<String, AlgoResult>,
    ensembleScore: Float,
    modifier: Modifier = Modifier,
) {
    var chartType by rememberSaveable { mutableStateOf(ContributionChartType.RADAR) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "알고리즘 기여도",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        onClick = { chartType = ContributionChartType.RADAR },
                        selected = chartType == ContributionChartType.RADAR,
                        label = { Text("레이더", fontSize = 11.sp) },
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        onClick = { chartType = ContributionChartType.WATERFALL },
                        selected = chartType == ContributionChartType.WATERFALL,
                        label = { Text("폭포수", fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when (chartType) {
                ContributionChartType.RADAR ->
                    AlgoRadarChartView(
                        algoResults = algoResults,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                    )
                ContributionChartType.WATERFALL ->
                    AlgoWaterfallChart(
                        algoResults = algoResults,
                        ensembleScore = ensembleScore,
                        modifier = Modifier.fillMaxWidth(),
                    )
            }
        }
    }
}
