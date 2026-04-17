package com.tinyoscillator.presentation.chart.ext

import com.tinyoscillator.domain.model.OhlcvPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

private val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")

/**
 * 일봉 OHLCV → 주봉 OHLCV 집계 (ISO week 기준).
 *
 * - open   = 주 첫 거래일의 open
 * - high   = max(high)
 * - low    = min(low)
 * - close  = 주 마지막 거래일의 close
 * - volume = sum(volume)
 * - date   = 주 마지막 거래일 ("yyyyMMdd")
 *
 * 인덱스는 0부터 재할당한다. 입력이 날짜 오름차순이 아니어도 그룹 내부에서
 * 다시 정렬하므로 안전하다. 파싱 불가능한 date를 가진 포인트는 제외한다.
 */
fun List<OhlcvPoint>.resampleToWeekly(): List<OhlcvPoint> {
    if (isEmpty()) return emptyList()

    return this
        .mapNotNull { p ->
            val parsed = runCatching { LocalDate.parse(p.date, fmt) }.getOrNull()
            if (parsed == null) null else p to parsed
        }
        .groupBy { (_, d) ->
            d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        }
        .toSortedMap()
        .values
        .mapIndexed { i, weekPairs ->
            val sorted = weekPairs.sortedBy { it.second }.map { it.first }
            val first = sorted.first()
            val last = sorted.last()
            OhlcvPoint(
                index = i,
                open = first.open,
                high = sorted.maxOf { it.high },
                low = sorted.minOf { it.low },
                close = last.close,
                volume = sorted.sumOf { it.volume },
                date = last.date,
            )
        }
}

/** OhlcvPoint 리스트 → 날짜 라벨 맵 (인덱스 → "MM/dd"). 주봉/일봉 공용. */
fun List<OhlcvPoint>.toDateLabelsFromOhlcv(): Map<Int, String> =
    mapIndexed { i, p ->
        val raw = p.date
        val label = if (raw.length == 8) "${raw.substring(4, 6)}/${raw.substring(6, 8)}" else raw
        i to label
    }.toMap()
