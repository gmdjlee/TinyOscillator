package com.tinyoscillator.presentation.chart.ext

/** 한국 원화 포맷: 73400 → "73,400원" */
fun Long.formatKRW(): String = "%,d원".format(this)

/** Float → Long 변환 후 원화 포맷 */
fun Float.formatKRW(): String = toLong().formatKRW()
