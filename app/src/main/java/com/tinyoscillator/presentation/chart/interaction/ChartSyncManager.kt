package com.tinyoscillator.presentation.chart.interaction

import android.view.MotionEvent
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartGestureListener

/**
 * 캔들 차트 ↔ 거래량 차트 크로스헤어 + 뷰포트 동기화
 *
 * 캔들 차트의 스크롤/줌 제스처를 거래량 차트에 미러링하고,
 * 탭 시 같은 x 인덱스를 거래량 차트에도 하이라이트합니다.
 */
class ChartSyncManager(
    private val candleChart: BarLineChartBase<*>,
    private val volumeChart: BarChart,
) {
    private val syncListener = object : OnChartGestureListener {
        override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {
            syncViewport()
        }

        override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {
            syncViewport()
        }

        override fun onChartSingleTapped(me: MotionEvent?) {
            me ?: return
            val h = candleChart.getHighlightByTouchPoint(me.x, me.y)
            if (h != null) {
                volumeChart.highlightValue(Highlight(h.x, 0, 0), false)
                volumeChart.invalidate()
            }
        }

        override fun onChartLongPressed(me: MotionEvent?) {}
        override fun onChartDoubleTapped(me: MotionEvent?) {}
        override fun onChartFling(
            me1: MotionEvent?, me2: MotionEvent?, vX: Float, vY: Float
        ) {
            syncViewport()
        }

        override fun onChartGestureStart(
            me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?
        ) {}

        override fun onChartGestureEnd(
            me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?
        ) {
            syncViewport()
        }
    }

    private fun syncViewport() {
        val matrix = candleChart.viewPortHandler.matrixTouch
        volumeChart.viewPortHandler.refresh(matrix, volumeChart, true)
    }

    fun attach() {
        candleChart.onChartGestureListener = syncListener
    }

    fun detach() {
        candleChart.onChartGestureListener = null
    }
}
