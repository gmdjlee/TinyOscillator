package com.tinyoscillator.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================================================================
// Kiwoom 테마 API DTO
//
// 방어적 설계 원칙:
//   - 모든 필드는 `String? = null` 또는 `Int? = null` (NPE 차단 + ignoreUnknownKeys 흡수)
//   - 숫자형은 String으로 받고 헬퍼로 정규화 (`+1234`/`-1234`/공백/빈값 흡수)
//   - `@SerialName`은 공식 명세 + bamjun/kiwoom-rest-api 소스 기반 추정값
//   - 첫 실기 갱신 시 ThemeRepository가 raw response를 Timber.d로 덤프 → logcat에서 키 검증
//   - 키 불일치 시 본 파일의 @SerialName만 보정 (다른 코드 변경 불필요)
// ============================================================================

// ============================================================================
// ka90001 - 테마그룹별요청
// ============================================================================

@Serializable
data class KiwoomThemeListResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("thema_grp") val themeGroups: List<KiwoomThemeGroupItem>? = null
)

@Serializable
data class KiwoomThemeGroupItem(
    @SerialName("thema_grp_cd") val themeCode: String? = null,
    @SerialName("thema_nm") val themeName: String? = null,
    @SerialName("stk_num") val stockCount: String? = null,
    @SerialName("flu_sig") val fluctuationSign: String? = null,
    @SerialName("flu_rt") val fluctuationRate: String? = null,
    @SerialName("rising_stk_num") val risingStockCount: String? = null,
    @SerialName("fall_stk_num") val fallingStockCount: String? = null,
    @SerialName("dt_prft_rt") val periodReturnRate: String? = null,
    @SerialName("main_stk") val mainStocks: String? = null
)

// ============================================================================
// ka90002 - 테마구성종목요청
// ============================================================================

@Serializable
data class KiwoomThemeStockResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("thema_comp_stk") val themeStocks: List<KiwoomThemeStockItem>? = null
)

@Serializable
data class KiwoomThemeStockItem(
    @SerialName("stk_cd") val stockCode: String? = null,
    @SerialName("stk_nm") val stockName: String? = null,
    @SerialName("cur_prc") val currentPrice: String? = null,
    @SerialName("flu_sig") val fluctuationSign: String? = null,
    @SerialName("pred_pre") val priorDiff: String? = null,
    @SerialName("flu_rt") val fluctuationRate: String? = null,
    @SerialName("acc_trde_qty") val accumulatedVolume: String? = null,
    @SerialName("dt_prft_rt_n") val periodReturnRate: String? = null
)

// ============================================================================
// 정규화 헬퍼
//   - `+1234` / `-1234` / `  1234  ` / 빈문자열 / null 모두 안전하게 흡수
//   - 응답에 부호 prefix가 섞여 있는 경우(예: 등락률 `+2.34`) 한 번에 제거
// ============================================================================

internal fun String?.toDoubleOrZero(): Double =
    this?.trim()?.removePrefix("+")?.toDoubleOrNull() ?: 0.0

internal fun String?.toLongOrZero(): Long =
    this?.trim()?.removePrefix("+")?.toLongOrNull() ?: 0L

internal fun String?.toIntOrZero(): Int =
    this?.trim()?.removePrefix("+")?.toIntOrNull() ?: 0

// ============================================================================
// 엔드포인트 & ID 상수
// ============================================================================

object ThemeApiEndpoints {
    const val THEME_BASE = "/api/dostk/thme"
}

object ThemeApiIds {
    const val THEME_GROUP_LIST = "ka90001"
    const val THEME_COMPONENT_STOCKS = "ka90002"
}
