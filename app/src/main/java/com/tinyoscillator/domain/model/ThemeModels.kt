package com.tinyoscillator.domain.model

import com.tinyoscillator.core.database.entity.ThemeGroupEntity
import com.tinyoscillator.core.database.entity.ThemeStockEntity

/**
 * Kiwoom ka90001 / ka90002 응답을 사용자에게 노출하기 위한 도메인 모델.
 *
 * Repository 단계에서 DTO → Entity → 본 도메인 모델 순으로 변환된다.
 * 모든 숫자 필드는 정규화된 값(Double/Int/Long)이므로 UI에서 별도의 파싱이 불필요.
 */
data class ThemeGroup(
    val themeCode: String,
    val themeName: String,
    val stockCount: Int,
    val fluRate: Double,
    val periodReturnRate: Double,
    val riseCount: Int,
    val fallCount: Int,
    val mainStocks: String,
    val lastUpdated: Long,
)

data class ThemeStock(
    val themeCode: String,
    val stockCode: String,
    val stockName: String,
    val currentPrice: Double,
    val priorDiff: Double,
    val fluRate: Double,
    val volume: Long,
    val periodReturnRate: Double,
    val lastUpdated: Long,
)

/**
 * 테마 목록 정렬 모드. UI 칩 row와 1:1 매핑.
 */
enum class ThemeSortMode {
    /** ka90001 `dt_prft_rt` 내림차순 — 기본값 */
    TOP_RETURN,

    /** 등락률 내림차순 */
    FLU_RATE,

    /** 테마명 오름차순 */
    NAME,

    /** 종목수 내림차순 */
    STOCK_COUNT,
}

/**
 * 테마 데이터 갱신 시 사용할 거래소 필터.
 *
 * Kiwoom ka90001/ka90002 명세의 `stex_tp` 파라미터에 매핑.
 * 1=KRX (기본), 2=NXT, 3=통합. 사용자가 Settings에서 선택.
 */
enum class ThemeExchange(val code: String, val displayName: String) {
    KRX("1", "KRX"),
    NXT("2", "NXT"),
    INTEGRATED("3", "통합"),
    ;

    companion object {
        fun fromCode(code: String?): ThemeExchange =
            entries.firstOrNull { it.code == code } ?: KRX
    }
}

/**
 * Worker / Repository → ViewModel로 흘리는 진행 상태 스트림.
 * EtfDataProgress와 형태를 일치시켜 CollectionProgressBar 등 공용 UI를 그대로 재사용한다.
 */
sealed class ThemeDataProgress {
    data class Loading(val message: String, val progress: Float = 0f) : ThemeDataProgress()
    data class Success(val themeCount: Int, val stockCount: Int) : ThemeDataProgress()
    data class Error(val message: String) : ThemeDataProgress()
}

// ============================================================================
// Entity → Domain 매퍼
// ============================================================================

internal fun ThemeGroupEntity.toDomain(): ThemeGroup = ThemeGroup(
    themeCode = themeCode,
    themeName = themeName,
    stockCount = stockCount,
    fluRate = fluRate,
    periodReturnRate = periodReturnRate,
    riseCount = riseCount,
    fallCount = fallCount,
    mainStocks = mainStocks,
    lastUpdated = lastUpdated,
)

internal fun ThemeStockEntity.toDomain(): ThemeStock = ThemeStock(
    themeCode = themeCode,
    stockCode = stockCode,
    stockName = stockName,
    currentPrice = currentPrice,
    priorDiff = priorDiff,
    fluRate = fluRate,
    volume = volume,
    periodReturnRate = periodReturnRate,
    lastUpdated = lastUpdated,
)
