package com.tinyoscillator.domain.model

import com.tinyoscillator.core.database.entity.EtfEntity

data class KrxCredentials(val id: String, val password: String) {
    override fun toString() = "KrxCredentials(id=$id, password=*****)"
}

data class EtfKeywordFilter(
    val includeKeywords: List<String>,
    val excludeKeywords: List<String>
)

sealed class EtfDataProgress {
    data class Loading(val message: String, val progress: Float = 0f) : EtfDataProgress()
    data class Success(val etfCount: Int, val holdingCount: Int) : EtfDataProgress()
    data class Error(val message: String) : EtfDataProgress()
}

sealed class EtfUiState {
    data object Idle : EtfUiState()
    data class Loading(val message: String, val progress: Float = 0f) : EtfUiState()
    data class Success(val etfCount: Int) : EtfUiState()
    data class Error(val message: String) : EtfUiState()
}

/**
 * 키워드 탭 — 하나의 포함 키워드 그룹.
 * 멤버 ETF는 name.contains(keyword) 매칭 결과.
 */
data class KeywordGroup(
    val keyword: String,
    val etfCount: Int,
    val avgChangeRate: Double,   // 멤버 changeRate 평균(null 제외). 유효값 0개면 0.0
    val lastUpdated: Long,       // 멤버 updatedAt 최대값. 멤버 없으면 그룹 자체가 제외됨
    val members: List<EtfEntity>
)

enum class KeywordSortMode { ETF_COUNT, AVG_RETURN, NAME }

/**
 * 수집된 ETF를 등록된 포함 키워드로 분류한다.
 * 각 키워드에 대해 `name.contains(키워드)`인 ETF를 멤버로 묶는다.
 * 멤버가 0개인 키워드는 결과에서 제외한다. 동일 ETF가 여러 키워드에 매칭되면
 * 각 그룹에 중복 등장한다(정상 동작).
 */
fun groupEtfsByKeyword(
    etfs: List<EtfEntity>,
    includeKeywords: List<String>,
    query: String,
    sort: KeywordSortMode,
): List<KeywordGroup> {
    val candidateKeywords = includeKeywords.distinct()
        .filter { keyword -> query.isBlank() || keyword.contains(query, ignoreCase = true) }

    val groups = candidateKeywords.mapNotNull { keyword ->
        val members = etfs.filter { it.name.contains(keyword) }
        if (members.isEmpty()) return@mapNotNull null

        val rates = members.mapNotNull { it.changeRate }
        val avgChangeRate = if (rates.isEmpty()) 0.0 else rates.average()
        val lastUpdated = members.maxOf { it.updatedAt }

        KeywordGroup(
            keyword = keyword,
            etfCount = members.size,
            avgChangeRate = avgChangeRate,
            lastUpdated = lastUpdated,
            members = members
        )
    }

    return when (sort) {
        KeywordSortMode.ETF_COUNT -> groups.sortedByDescending { it.etfCount }
        KeywordSortMode.AVG_RETURN -> groups.sortedByDescending { it.avgChangeRate }
        KeywordSortMode.NAME -> groups.sortedBy { it.keyword }
    }
}
