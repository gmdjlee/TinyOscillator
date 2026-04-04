package com.tinyoscillator.presentation.chart.renderer

import android.content.Context
import android.graphics.Canvas
import com.github.mikephil.charting.charts.CombinedChart

/**
 * CombinedChart 서브클래스 — 캔들 위에 패턴 마커를 추가로 그림.
 * onDraw 사이클에서 super 렌더링 후 PatternMarkerRenderer를 호출.
 */
class PatternCombinedChart(context: Context) : CombinedChart(context) {

    var patternRenderer: PatternMarkerRenderer? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        patternRenderer?.draw(canvas)
    }
}
