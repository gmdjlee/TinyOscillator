package com.tinyoscillator.presentation.financial

object WiseReportUrl {
    private const val BASE =
        "https://navercomp.wisereport.co.kr/company/c1010001.aspx"

    /** @param ticker KRX 6-digit code, e.g. "000660" */
    fun of(ticker: String): String = "$BASE?cmp_cd=$ticker"
}
