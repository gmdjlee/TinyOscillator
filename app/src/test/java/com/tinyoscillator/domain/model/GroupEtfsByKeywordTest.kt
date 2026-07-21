package com.tinyoscillator.domain.model

import com.tinyoscillator.core.database.entity.EtfEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * `groupEtfsByKeyword` 순수함수 단위 테스트.
 *
 * 키워드 탭(Phase 0) — ETF를 포함 키워드로 분류하는 로직 검증.
 * 골든 규칙: 멤버0 제외, avg는 non-null만, 정렬 3종, query 필터.
 */
class GroupEtfsByKeywordTest {

    private fun etf(
        ticker: String,
        name: String,
        changeRate: Double? = null,
        updatedAt: Long = 0L,
    ) = EtfEntity(
        ticker = ticker,
        name = name,
        isinCode = "",
        changeRate = changeRate,
        updatedAt = updatedAt,
    )

    @Test
    fun `기본 그룹핑 - ETF 3개 키워드 2개면 각 키워드 그룹에 올바른 멤버와 etfCount`() {
        val etfs = listOf(
            etf("069500", "KODEX 반도체", changeRate = 1.0),
            etf("091160", "TIGER 반도체", changeRate = 2.0),
            etf("305720", "KODEX 2차전지산업", changeRate = 3.0),
        )
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체", "2차전지"),
            query = "",
            sort = KeywordSortMode.NAME,
        )

        assertEquals(2, result.size)

        val semiconductor = result.first { it.keyword == "반도체" }
        assertEquals(2, semiconductor.etfCount)
        assertEquals(setOf("069500", "091160"), semiconductor.members.map { it.ticker }.toSet())

        val battery = result.first { it.keyword == "2차전지" }
        assertEquals(1, battery.etfCount)
        assertEquals("305720", battery.members.single().ticker)
    }

    @Test
    fun `멤버 0개 키워드는 결과에서 제외된다`() {
        val etfs = listOf(
            etf("069500", "KODEX 반도체", changeRate = 1.0),
        )
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체", "바이오"),
            query = "",
            sort = KeywordSortMode.NAME,
        )

        assertEquals(1, result.size)
        assertEquals("반도체", result.single().keyword)
        assertTrue(result.none { it.keyword == "바이오" })
    }

    @Test
    fun `한 ETF가 여러 키워드에 매칭되면 각 그룹에 모두 등장한다`() {
        val etfs = listOf(
            etf("123456", "KODEX 반도체2차전지", changeRate = 1.0),
        )
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체", "2차전지"),
            query = "",
            sort = KeywordSortMode.NAME,
        )

        assertEquals(2, result.size)
        assertTrue(result.all { it.members.any { m -> m.ticker == "123456" } })
    }

    @Test
    fun `avgChangeRate는 non-null 값만 평균한다 - 전부 null이면 0dot0`() {
        val etfsMixed = listOf(
            etf("A", "반도체A", changeRate = 2.0),
            etf("B", "반도체B", changeRate = null),
            etf("C", "반도체C", changeRate = 4.0),
        )
        val mixedResult = groupEtfsByKeyword(
            etfs = etfsMixed,
            includeKeywords = listOf("반도체"),
            query = "",
            sort = KeywordSortMode.NAME,
        )
        assertEquals(3.0, mixedResult.single().avgChangeRate, 1e-10)

        val etfsAllNull = listOf(
            etf("A", "반도체A", changeRate = null),
            etf("B", "반도체B", changeRate = null),
        )
        val allNullResult = groupEtfsByKeyword(
            etfs = etfsAllNull,
            includeKeywords = listOf("반도체"),
            query = "",
            sort = KeywordSortMode.NAME,
        )
        assertEquals(0.0, allNullResult.single().avgChangeRate, 1e-10)
    }

    @Test
    fun `lastUpdated는 멤버 updatedAt 최대값이다`() {
        val etfs = listOf(
            etf("A", "반도체A", updatedAt = 100L),
            etf("B", "반도체B", updatedAt = 300L),
            etf("C", "반도체C", updatedAt = 200L),
        )
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체"),
            query = "",
            sort = KeywordSortMode.NAME,
        )
        assertEquals(300L, result.single().lastUpdated)
    }

    @Test
    fun `정렬 ETF_COUNT는 etfCount 내림차순이다`() {
        val etfs = listOf(
            etf("A", "반도체A"),
            etf("B", "2차전지B"),
            etf("C", "2차전지C"),
            etf("D", "2차전지D"),
        )
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체", "2차전지"),
            query = "",
            sort = KeywordSortMode.ETF_COUNT,
        )
        assertEquals(listOf("2차전지", "반도체"), result.map { it.keyword })
        assertEquals(listOf(3, 1), result.map { it.etfCount })
    }

    @Test
    fun `정렬 AVG_RETURN은 avgChangeRate 내림차순이다`() {
        val etfs = listOf(
            etf("A", "반도체A", changeRate = 1.0),
            etf("B", "2차전지B", changeRate = 5.0),
        )
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체", "2차전지"),
            query = "",
            sort = KeywordSortMode.AVG_RETURN,
        )
        assertEquals(listOf("2차전지", "반도체"), result.map { it.keyword })
    }

    @Test
    fun `정렬 NAME은 keyword 오름차순이다`() {
        val etfs = listOf(
            etf("A", "반도체A"),
            etf("B", "2차전지B"),
            etf("C", "바이오C"),
        )
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체", "2차전지", "바이오"),
            query = "",
            sort = KeywordSortMode.NAME,
        )
        assertEquals(listOf("2차전지", "바이오", "반도체"), result.map { it.keyword })
    }

    @Test
    fun `query 필터 - 대소문자 무시하고 매칭되는 키워드만 남긴다`() {
        val etfs = listOf(
            etf("A", "반도체A", changeRate = 1.0),
            etf("B", "2차전지B", changeRate = 2.0),
        )
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체", "2차전지"),
            query = "반도",
            sort = KeywordSortMode.NAME,
        )
        assertEquals(listOf("반도체"), result.map { it.keyword })
    }

    @Test
    fun `빈 includeKeywords는 빈 리스트를 반환한다`() {
        val etfs = listOf(etf("A", "반도체A"))
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = emptyList(),
            query = "",
            sort = KeywordSortMode.NAME,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `빈 etfs는 빈 리스트를 반환한다`() {
        val result = groupEtfsByKeyword(
            etfs = emptyList(),
            includeKeywords = listOf("반도체", "2차전지"),
            query = "",
            sort = KeywordSortMode.NAME,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `includeKeywords에 중복 문자열이 있어도 결과 그룹은 한 번만 등장한다`() {
        val etfs = listOf(etf("A", "반도체A", changeRate = 1.0))
        val result = groupEtfsByKeyword(
            etfs = etfs,
            includeKeywords = listOf("반도체", "반도체"),
            query = "",
            sort = KeywordSortMode.NAME,
        )
        assertEquals(1, result.size)
        assertEquals("반도체", result.single().keyword)
    }
}
