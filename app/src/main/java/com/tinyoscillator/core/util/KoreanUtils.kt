package com.tinyoscillator.core.util

/**
 * Korean text utilities for chosung (initial consonant) extraction and matching.
 * Pure Kotlin implementation — no external library needed.
 */
object KoreanUtils {

    private val CHOSUNG = charArrayOf(
        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
        'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
    )

    private val CHOSUNG_SET = CHOSUNG.toSet()
    private const val GA = 0xAC00
    private const val TOTAL = 11172 // 가~힣

    /** "삼성전자" -> "ㅅㅅㅈㅈ" */
    fun extractChosung(text: String): String = buildString(text.length) {
        for (ch in text) {
            val code = ch.code
            if (code in GA until GA + TOTAL) {
                append(CHOSUNG[(code - GA) / (21 * 28)])
            }
        }
    }

    /** 쿼리가 초성으로만 이루어져 있는지 판단 */
    fun isChosungOnly(query: String): Boolean =
        query.isNotBlank() && query.all { it in CHOSUNG_SET }

    /**
     * 검색 매칭:
     * 1. 초성 쿼리 -> 종목명 초성에 포함되는지
     * 2. 일반 쿼리 -> 종목명/티커에 포함되는지 (대소문자 무시)
     */
    fun matches(
        query: String,
        name: String,
        ticker: String,
        nameChosung: String,
        nameEng: String = "",
    ): Boolean {
        val q = query.trim()
        if (q.isBlank()) return true
        if (isChosungOnly(q)) return nameChosung.contains(q)
        val qLower = q.lowercase()
        return name.contains(q, ignoreCase = true)
            || ticker.contains(q, ignoreCase = true)
            || nameEng.contains(qLower, ignoreCase = true)
    }
}
