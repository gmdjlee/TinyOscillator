package com.tinyoscillator.domain.usecase

import com.tinyoscillator.domain.model.OhlcvPoint
import com.tinyoscillator.domain.model.PatternResult
import com.tinyoscillator.domain.model.PatternType
import kotlin.math.abs

object CandlePatternDetector {

    private data class C(val o: Float, val h: Float, val l: Float, val c: Float) {
        val body get() = abs(c - o)
        val range get() = h - l
        val upper get() = h - maxOf(o, c)
        val lower get() = minOf(o, c) - l
        val isBull get() = c > o
        val midpoint get() = (o + c) / 2f
    }

    private fun OhlcvPoint.toC() = C(open, high, low, close)

    fun detect(candles: List<OhlcvPoint>): List<PatternResult> {
        val results = mutableListOf<PatternResult>()
        candles.forEachIndexed { i, raw ->
            val c = raw.toC()
            val p1 = if (i > 0) candles[i - 1].toC() else null
            val p2 = if (i > 1) candles[i - 2].toC() else null
            detectSingle(i, c, results)
            if (p1 != null) detectTwo(i, p1, c, results)
            if (p1 != null && p2 != null) detectThree(i, p2, p1, c, results)
        }
        return results
    }

    private fun detectSingle(i: Int, c: C, out: MutableList<PatternResult>) {
        if (c.range < 1f) return
        if (c.body / c.range < 0.08f)
            out += PatternResult(i, PatternType.DOJI,
                (1f - c.body / c.range / 0.08f).coerceIn(0f, 1f))
        if (c.lower > c.body * 2f && c.upper < c.body * 0.5f && c.body > 0f)
            out += PatternResult(i, PatternType.HAMMER,
                (c.lower / (c.body * 2f)).coerceAtMost(1f))
        if (c.upper > c.body * 2f && c.lower < c.body * 0.5f && c.body > 0f)
            out += PatternResult(i, PatternType.INVERTED_HAMMER,
                (c.upper / (c.body * 2f)).coerceAtMost(1f))
        if (c.upper > c.body * 2f && c.lower < c.body * 0.3f && !c.isBull)
            out += PatternResult(i, PatternType.SHOOTING_STAR,
                (c.upper / c.range).coerceAtMost(1f))
    }

    private fun detectTwo(i: Int, p: C, c: C, out: MutableList<PatternResult>) {
        if (p.isBull && !c.isBull && c.lower > c.body * 2f && c.upper < c.body * 0.5f)
            out += PatternResult(i, PatternType.HANGING_MAN, 0.7f)
        if (!p.isBull && c.isBull && c.o < p.c && c.c > p.o && c.body > p.body)
            out += PatternResult(i, PatternType.BULLISH_ENGULFING,
                (c.body / p.body.coerceAtLeast(0.01f)).coerceAtMost(1f))
        if (p.isBull && !c.isBull && c.o > p.c && c.c < p.o && c.body > p.body)
            out += PatternResult(i, PatternType.BEARISH_ENGULFING,
                (c.body / p.body.coerceAtLeast(0.01f)).coerceAtMost(1f))
        if (!p.isBull && c.isBull && c.o < p.l && c.c > p.midpoint)
            out += PatternResult(i, PatternType.PIERCING_LINE,
                ((c.c - p.midpoint) / (p.o - p.c + 1e-6f)).coerceIn(0f, 1f))
        if (p.isBull && !c.isBull && c.o > p.h && c.c < p.midpoint)
            out += PatternResult(i, PatternType.DARK_CLOUD_COVER,
                ((p.midpoint - c.c) / (p.c - p.o + 1e-6f)).coerceIn(0f, 1f))
    }

    private fun detectThree(i: Int, p2: C, p1: C, c: C, out: MutableList<PatternResult>) {
        if (!p2.isBull && p1.body < p2.body * 0.3f && c.isBull && c.c > p2.midpoint)
            out += PatternResult(i, PatternType.MORNING_STAR, 0.8f)
        if (p2.isBull && p1.body < p2.body * 0.3f && !c.isBull && c.c < p2.midpoint)
            out += PatternResult(i, PatternType.EVENING_STAR, 0.8f)
        if (p2.isBull && p1.isBull && c.isBull &&
            p1.o > p2.o && p1.o < p2.c && c.o > p1.o && c.o < p1.c)
            out += PatternResult(i, PatternType.THREE_WHITE_SOLDIERS, 0.9f)
        if (!p2.isBull && !p1.isBull && !c.isBull &&
            p1.o < p2.o && p1.o > p2.c && c.o < p1.o && c.o > p1.c)
            out += PatternResult(i, PatternType.THREE_BLACK_CROWS, 0.9f)
    }
}
