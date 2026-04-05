package com.tinyoscillator.domain.model

/**
 * KRX 섹터 또는 사용자 정의 테마 그룹을 표현하는 도메인 모델.
 */
data class StockGroup(
    val id: Long,
    val name: String,
    val type: GroupType,
    val tickers: List<String>,
    val avgSignal: Float = 0.5f,
    val topSignalTicker: String = "",
    val memberCount: Int = 0,
)

enum class GroupType {
    KRX_SECTOR,
    USER_THEME,
}

/** 앱 최초 설치 시 자동 생성되는 기본 테마 목록 */
val DEFAULT_THEMES = listOf(
    Pair("K-방산", listOf("012450", "047810", "003490", "272210")),
    Pair("2차전지", listOf("373220", "006400", "247540", "112610")),
    Pair("조선", listOf("009540", "010140", "042660")),
    Pair("반도체", listOf("005930", "000660", "058470", "042700")),
    Pair("바이오", listOf("207940", "068270", "128940")),
)
