package com.tinyoscillator.core.util

/**
 * 스크래퍼/리포지토리 공통 파싱 유틸.
 *
 * Equity/FnGuide 리포트 스크래퍼와 ConsensusRepository에 중복되어 있던
 * parseDate / parsePrice 구현을 한 곳에서 관리한다.
 */
object ParsingUtils {

    /**
     * "26/03/23" → "2026-03-23"
     *
     * 입력 형식: `yy/MM/dd` 또는 `yyyy/MM/dd`.
     * 두 자리 연도는 `20xx`로 확장, 월/일은 0 패딩.
     * 잘못된 형식이거나 빈 문자열이면 null 반환.
     */
    fun parseSlashDate(dateStr: String): String? {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split("/")
        if (parts.size != 3) return null

        val year = parts[0].trim()
        val month = parts[1].trim().padStart(2, '0')
        val day = parts[2].trim().padStart(2, '0')

        val fullYear = if (year.length == 2) "20$year" else year
        return "$fullYear-$month-$day"
    }

    /**
     * "300,000" → 300000L, "0" / "-" / 빈문자열 → 0L
     *
     * 3자리 콤마 구분된 가격 문자열을 Long으로 변환한다.
     * 파싱 실패 시 0L 반환 (음수 가격은 의미 없음).
     */
    fun parsePriceLong(priceStr: String): Long {
        val cleaned = priceStr.replace(",", "").trim()
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == "0") return 0L
        return cleaned.toLongOrNull() ?: 0L
    }
}
