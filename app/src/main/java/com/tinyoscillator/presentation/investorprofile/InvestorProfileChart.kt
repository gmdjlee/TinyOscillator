package com.tinyoscillator.presentation.investorprofile

import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet
import com.tinyoscillator.domain.model.InvestorPriceBin
import com.tinyoscillator.domain.model.InvestorType
import com.tinyoscillator.domain.model.InvestorVolumeProfile
import com.tinyoscillator.domain.model.ProfileDisplayMode
import com.tinyoscillator.presentation.chart.ChartTheme
import com.tinyoscillator.presentation.chart.ext.formatKRW
import com.tinyoscillator.presentation.chart.formatter.KoreanPriceFormatter
import com.tinyoscillator.presentation.chart.formatter.KoreanVolumeFormatter
import com.tinyoscillator.presentation.chart.rememberChartTheme
import kotlin.math.roundToLong

/**
 * 주체별 매물대 차트. MPAndroidChart `HorizontalBarChart`를 `AndroidView`로 감싼다.
 * 명세: `docs/TASK_investor_volume_profile.md` §7.3.
 *
 * 가격축(세로, 위=고가)은 `xAxis`([XAxis.XAxisPosition.BOTTOM]이 HorizontalBarChart에서는
 * 왼쪽에 그려진다), 물량축(가로)은 `axisRight`(HorizontalBarChart는 axisLeft를 위, axisRight를
 * 아래에 그린다)를 쓴다. 색은 [ChartTheme] 파생만 사용한다.
 */
@Composable
fun InvestorProfileChart(
    profile: InvestorVolumeProfile,
    selected: Set<InvestorType>,
    displayMode: ProfileDisplayMode,
    modifier: Modifier = Modifier
) {
    val theme = rememberChartTheme()
    val description = remember(profile) { buildChartDescription(profile) }
    val lastBound = remember {
        arrayOfNulls<Triple<InvestorVolumeProfile, Set<InvestorType>, ProfileDisplayMode>>(1)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "주체별 매물대",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            AndroidView(
                factory = { context ->
                    HorizontalBarChart(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setupInvestorProfileChart(this)
                    }
                },
                update = { chart ->
                    applyInvestorProfileTheme(chart, theme)
                    val key = Triple(profile, selected, displayMode)
                    if (key != lastBound[0]) {
                        bindInvestorProfileData(chart, profile, selected, displayMode, theme)
                        lastBound[0] = key
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .semantics { contentDescription = description }
                    .testTag("InvestorProfileChart")
            )
        }
    }
}

private fun setupInvestorProfileChart(chart: HorizontalBarChart) {
    chart.apply {
        description.isEnabled = false
        setDrawGridBackground(false)
        setDrawBarShadow(false)
        setScaleEnabled(false)
        isDoubleTapToZoomEnabled = false
        setFitBars(true)
        setExtraOffsets(8f, 8f, 8f, 8f)

        axisLeft.isEnabled = false
        axisRight.axisMinimum = 0f

        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            valueFormatter = KoreanPriceFormatter()
        }
        axisRight.valueFormatter = KoreanVolumeFormatter()

        legend.isEnabled = true
    }
}

/** `update` 람다에서 매 recomposition 색을 재적용한다(다크 전환 시 축 라벨 소실 방지, `DemarkTDChart.kt` 관례). */
private fun applyInvestorProfileTheme(chart: HorizontalBarChart, theme: ChartTheme) {
    chart.xAxis.textColor = theme.axisText
    chart.axisRight.textColor = theme.axisText
    chart.axisRight.gridColor = theme.grid
    chart.legend.textColor = theme.axisText
}

private fun colorFor(type: InvestorType, theme: ChartTheme): Int = when (type) {
    InvestorType.INDIVIDUAL -> theme.emphasisLine
    InvestorType.FOREIGN -> theme.neutralLine
    InvestorType.INSTITUTION -> theme.tertiaryLine
}

private fun bindInvestorProfileData(
    chart: HorizontalBarChart,
    profile: InvestorVolumeProfile,
    selected: Set<InvestorType>,
    displayMode: ProfileDisplayMode,
    theme: ChartTheme
) {
    val bins = profile.bins
    // 표시 순서 고정: 개인 → 외국인 → 기관(선언 순서). 선택된 주체만 필터.
    val orderedSelected = InvestorType.entries.filter { it in selected }

    chart.xAxis.apply {
        axisMinimum = bins.first().lower.toFloat()
        axisMaximum = bins.last().upper.toFloat()
        granularity = profile.binWidth.toFloat()
    }

    val barData = if (displayMode == ProfileDisplayMode.STACKED || orderedSelected.size < 2) {
        buildStackedBarData(bins, profile, orderedSelected, theme)
    } else {
        buildGroupedBarData(bins, profile, orderedSelected, theme)
    }
    chart.data = barData

    // GROUPED이면서 선택 2개 이상일 때만 groupBars 호출(1개 이하면 RuntimeException).
    if (displayMode == ProfileDisplayMode.GROUPED && orderedSelected.size >= 2) {
        chart.groupBars(bins.first().lower.toFloat(), (profile.binWidth * 0.18).toFloat(), 0f)
    }

    addLimitLines(chart, profile, theme)

    chart.notifyDataSetChanged()
    chart.invalidate()
}

/** STACKED 표시, 그리고 GROUPED이지만 선택 주체가 1개 이하인 경우 공용. */
private fun buildStackedBarData(
    bins: List<InvestorPriceBin>,
    profile: InvestorVolumeProfile,
    orderedSelected: List<InvestorType>,
    theme: ChartTheme
): BarData {
    val seriesLabels = orderedSelected.map { it.label }.toTypedArray()
    val seriesColors = orderedSelected.map { colorFor(it, theme) }
    val entries = bins.indices.map { i ->
        val values = orderedSelected.map { type ->
            (profile.byInvestor[type]?.getOrNull(i) ?: 0.0).toFloat()
        }.toFloatArray()
        BarEntry(bins[i].center.toFloat(), values)
    }
    // 범례: 스택 2개 이상이면 stackLabels가 항목이 되므로 데이터셋 라벨은 비운다(빈 항목 노출 방지).
    // 단일 주체(스택 1개)는 MPAndroidChart가 스택으로 취급하지 않아 데이터셋 라벨이 범례에 쓰인다.
    val setLabel = if (orderedSelected.size == 1) orderedSelected.first().label else ""
    val set = BarDataSet(entries, setLabel).apply {
        stackLabels = seriesLabels
        colors = seriesColors
        axisDependency = YAxis.AxisDependency.RIGHT
        barBorderWidth = 1f
        barBorderColor = theme.holeFill
        setDrawValues(false)
    }
    val dataSets = mutableListOf<IBarDataSet>(set)
    return BarData(dataSets).apply {
        barWidth = (profile.binWidth * 0.82).toFloat()
    }
}

/** GROUPED 표시, 선택 주체 2개 이상. 각 데이터셋은 반드시 `bins.size`개 엔트리를 가져야 한다. */
private fun buildGroupedBarData(
    bins: List<InvestorPriceBin>,
    profile: InvestorVolumeProfile,
    orderedSelected: List<InvestorType>,
    theme: ChartTheme
): BarData {
    val n = orderedSelected.size
    val dataSets = mutableListOf<IBarDataSet>()
    orderedSelected.forEach { type ->
        val values = profile.byInvestor[type]
        val entries = bins.indices.map { i -> BarEntry(i.toFloat(), (values?.getOrNull(i) ?: 0.0).toFloat()) }
        dataSets.add(
            BarDataSet(entries, type.label).apply {
                color = colorFor(type, theme)
                axisDependency = YAxis.AxisDependency.RIGHT
                setDrawValues(false)
            }
        )
    }
    return BarData(dataSets).apply {
        barWidth = (profile.binWidth * 0.82 / n).toFloat()
    }
}

/** 기준가·캡·지지 3개로 제한(가격축). 재바인딩마다 전부 지우고 다시 추가한다. */
private fun addLimitLines(chart: HorizontalBarChart, profile: InvestorVolumeProfile, theme: ChartTheme) {
    val xAxis = chart.xAxis
    xAxis.removeAllLimitLines()

    val basePriceLine = LimitLine(profile.basePrice.toFloat(), "기준가 " + profile.basePrice.formatKRW()).apply {
        enableDashedLine(12f, 6f, 0f)
        lineColor = theme.axisText
        textColor = theme.axisText
        labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
    }
    xAxis.addLimitLine(basePriceLine)

    profile.metrics.capZone?.let { zone ->
        val price = profile.bins[zone.last].upper.toFloat()
        val capLine = LimitLine(price, "캡 " + price.formatKRW()).apply {
            lineWidth = 1f
            lineColor = theme.positive
            textColor = theme.positive
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
        }
        xAxis.addLimitLine(capLine)
    }

    profile.metrics.supportZone?.let { zone ->
        val price = profile.bins[zone.first].lower.toFloat()
        val supportLine = LimitLine(price, "지지 " + price.formatKRW()).apply {
            lineWidth = 1f
            lineColor = theme.negative
            textColor = theme.negative
            labelPosition = LimitLine.LimitLabelPosition.RIGHT_BOTTOM
        }
        xAxis.addLimitLine(supportLine)
    }
}

/**
 * 접근성 요약. 스윙 전환점이 있으면(anchorDate/anchorPrice) 프리픽스에 포함한다 — [InvestorVolumeProfile]은
 * 어느 방향(고점/저점)의 전환점인지 별도 필드로 담지 않으므로(§4) 값 자체만 표기한다.
 */
private fun buildChartDescription(profile: InvestorVolumeProfile): String {
    val prefix = if (profile.anchorDate != null && profile.anchorPrice != null) {
        "스윙 전환점(${profile.anchorDate} · ${profile.anchorPrice.formatKRW()}) 이후 주체별 매물대."
    } else {
        "주체별 매물대."
    }
    val base = " 기준가 ${profile.basePrice.formatKRW()}."
    val cap = profile.metrics.capZone?.let { zone ->
        val lower = profile.bins[zone.first].lower.roundToLong().formatKRW()
        val upper = profile.bins[zone.last].upper.roundToLong().formatKRW()
        val days = "%.1f".format(profile.metrics.capZoneVolume / profile.metrics.averageDailyVolume)
        " 상방 캡 ${lower}에서 ${upper}, ${profile.metrics.capZoneVolume.toKoreanShares()}, 일평균 ${days}일분."
    }.orEmpty()
    val support = profile.metrics.supportZone?.let { zone ->
        val lower = profile.bins[zone.first].lower.roundToLong().formatKRW()
        val upper = profile.bins[zone.last].upper.roundToLong().formatKRW()
        " 하방 지지 ${lower}에서 ${upper}, ${profile.metrics.supportZoneVolume.toKoreanShares()}."
    }.orEmpty()
    return prefix + base + cap + support
}

/** 물량 표기(≥1억: 억주, ≥1만: 만주, 그 외: 주). `InvestorProfileContent.kt`와 동일 규칙(§7.4), 파일 간 공유하지 않는다. */
private fun Double.toKoreanShares(): String = when {
    this >= 1e8 -> "%.1f억주".format(this / 1e8)
    this >= 1e4 -> "%,d만주".format((this / 1e4).roundToLong())
    else -> "%,d주".format(this.roundToLong())
}
