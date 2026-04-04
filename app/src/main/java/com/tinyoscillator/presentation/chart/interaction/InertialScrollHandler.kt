package com.tinyoscillator.presentation.chart.interaction

import android.view.Choreographer
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.OverScroller
import com.github.mikephil.charting.charts.BarLineChartBase

/**
 * 핀치줌 후 관성 스크롤 핸들러
 *
 * VelocityTracker로 플링 속도를 감지하고 OverScroller + Choreographer로
 * 부드러운 관성 애니메이션을 적용합니다.
 */
class InertialScrollHandler(
    private val chart: BarLineChartBase<*>,
) {
    private val scroller = OverScroller(chart.context)
    private val velocityTracker = VelocityTracker.obtain()
    private var lastX = 0f

    /**
     * Compose의 pointerInput 또는 View.OnTouchListener에서 호출.
     * @return true if the event was consumed
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker.addMovement(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                lastX = event.x
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                chart.moveViewToX(chart.lowestVisibleX - dx / chart.scaleX)
                lastX = event.x
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker.computeCurrentVelocity(1000)
                val vx = velocityTracker.xVelocity
                if (kotlin.math.abs(vx) > 50f) {
                    scroller.fling(
                        chart.lowestVisibleX.toInt(), 0,
                        (-vx / chart.scaleX).toInt(), 0,
                        chart.xChartMin.toInt(), chart.xChartMax.toInt(),
                        0, 0,
                    )
                    animateFling()
                }
                velocityTracker.clear()
            }
        }
        return true
    }

    private fun animateFling() {
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (scroller.computeScrollOffset()) {
                    chart.moveViewToX(scroller.currX.toFloat())
                    choreographer.postFrameCallback(this)
                }
            }
        }
        choreographer.postFrameCallback(callback)
    }

    /** 리소스 해제 */
    fun release() {
        scroller.forceFinished(true)
        velocityTracker.recycle()
    }
}
