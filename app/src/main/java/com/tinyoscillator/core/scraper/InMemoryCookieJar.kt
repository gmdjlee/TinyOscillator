package com.tinyoscillator.core.scraper

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 메모리 기반 CookieJar — 세션 쿠키를 유지하기 위한 구현.
 */
class InMemoryCookieJar : CookieJar {

    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val stored = cookieStore.getOrPut(host) { mutableListOf() }
        for (cookie in cookies) {
            stored.removeAll { it.name == cookie.name }
            stored.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}
