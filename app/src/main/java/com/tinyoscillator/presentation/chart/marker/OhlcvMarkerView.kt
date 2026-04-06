package com.tinyoscillator.presentation.chart.marker

import android.content.Context
import android.view.View
import android.widget.TextView
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.CandleEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.tinyoscillator.R
import com.tinyoscillator.presentation.chart.ext.formatKRW

/**
 * OHLCV 캔들 마커 — 터치 시 시가/고가/저가/종가 + 패턴 표시
 * 화면 오른쪽 절반에서는 왼쪽으로 팝업 (경계 감지)
 */
class OhlcvMarkerView(
    context: Context,
    private val dateLabels: Map<Int, String> = emptyMap(),
    private val extraPatterns: Map<Int, List<String>> = emptyMap(),
) : MarkerView(context, R.layout.view_ohlcv_marker) {

    private val tvDate: TextView = findViewById(R.id.tv_marker_date)
    private val tvOpen: TextView = findViewById(R.id.tv_marker_open)
    private val tvHigh: TextView = findViewById(R.id.tv_marker_high)
    private val tvLow: TextView = findViewById(R.id.tv_marker_low)
    private val tvClose: TextView = findViewById(R.id.tv_marker_close)
    private val tvPattern: TextView = findViewById(R.id.tv_marker_pattern)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e !is CandleEntry) {
            super.refreshContent(e, highlight)
            return
        }
        val idx = e.x.toInt()
        tvDate.text = dateLabels[idx] ?: ""
        tvOpen.text = "시가  ${e.open.toLong().formatKRW()}"
        tvHigh.text = "고가  ${e.high.toLong().formatKRW()}"
        tvLow.text = "저가  ${e.low.toLong().formatKRW()}"
        tvClose.text = "종가  ${e.close.toLong().formatKRW()}"

        val patterns = extraPatterns[idx]
        if (!patterns.isNullOrEmpty()) {
            tvPattern.text = "신호: ${patterns.joinToString(" · ")}"
            tvPattern.visibility = View.VISIBLE
        } else {
            tvPattern.visibility = View.GONE
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        val offsetX = if (chartView != null &&
            getXChartPosition() > chartView!!.width / 2f
        ) -(width.toFloat())
        else 0f
        return MPPointF(offsetX, -(height.toFloat()))
    }

    /** 현재 하이라이트의 차트 상 X 픽셀 좌표 */
    private fun getXChartPosition(): Float {
        val chart = chartView as? BarLineChartBase<*> ?: return 0f
        val highlight = chart.highlighted?.firstOrNull() ?: return 0f
        val transformer = chart.getTransformer(YAxis.AxisDependency.LEFT)
        val pts = floatArrayOf(highlight.x, 0f)
        transformer.pointValuesToPixel(pts)
        return pts[0]
    }
}
