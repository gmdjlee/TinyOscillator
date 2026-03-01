package com.tinyoscillator.core.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Kiwoom API 표준 응답 래퍼.
 */
@Serializable
data class ApiResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null
)

/**
 * Kiwoom OAuth 토큰 응답 (au10001).
 */
@Serializable
data class TokenResponse(
    @SerialName("return_code") val returnCode: Int = 0,
    @SerialName("return_msg") val returnMsg: String? = null,
    @SerialName("token") val token: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_dt") val expiresDt: String? = null
)

/**
 * KIS OAuth 토큰 응답.
 */
@Serializable
data class KisTokenResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val expires_in: Long? = null,
    val access_token_token_expired: String? = null
)

/**
 * API 오류 타입.
 */
sealed class ApiError(override val message: String) : Exception(message) {
    class AuthError(msg: String) : ApiError(msg)
    class NetworkError(msg: String) : ApiError(msg)
    class ApiCallError(val code: Int, msg: String) : ApiError("[$code] $msg")
    class ParseError(msg: String) : ApiError(msg)
    class TimeoutError(msg: String) : ApiError(msg)
    class NoApiKeyError(
        msg: String = "API 키가 설정되지 않았습니다. 설정에서 API 키를 입력해주세요."
    ) : ApiError(msg)
}

/**
 * 투자 모드.
 */
enum class InvestmentMode(val displayName: String) {
    MOCK("모의투자"),
    PRODUCTION("실전투자")
}

/**
 * Kiwoom API 키 설정.
 */
data class KiwoomApiKeyConfig(
    val appKey: String = "",
    val secretKey: String = "",
    val investmentMode: InvestmentMode = InvestmentMode.MOCK
) {
    fun isValid(): Boolean = appKey.isNotBlank() && secretKey.isNotBlank()

    fun getBaseUrl(): String = when (investmentMode) {
        InvestmentMode.MOCK -> "https://mockapi.kiwoom.com"
        InvestmentMode.PRODUCTION -> "https://api.kiwoom.com"
    }
}

/**
 * KIS API 키 설정.
 */
data class KisApiKeyConfig(
    val appKey: String = "",
    val appSecret: String = "",
    val investmentMode: InvestmentMode = InvestmentMode.MOCK
) {
    fun isValid(): Boolean = appKey.isNotBlank() && appSecret.isNotBlank()

    fun getBaseUrl(): String = when (investmentMode) {
        InvestmentMode.MOCK -> "https://openapivts.koreainvestment.com:29443"
        InvestmentMode.PRODUCTION -> "https://openapi.koreainvestment.com:9443"
    }
}

/**
 * 토큰 정보.
 */
data class TokenInfo(
    val token: String,
    val expiresAtMillis: Long,
    val tokenType: String = "bearer"
) {
    val bearer: String get() = "Bearer $token"

    fun isExpired(): Boolean {
        return System.currentTimeMillis() >= expiresAtMillis - 60_000 // 1분 전 만료 처리
    }
}
