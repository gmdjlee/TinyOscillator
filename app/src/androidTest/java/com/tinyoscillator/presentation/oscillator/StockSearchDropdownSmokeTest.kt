package com.tinyoscillator.presentation.oscillator

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tinyoscillator.core.database.entity.StockMasterEntity
import com.tinyoscillator.presentation.common.StockSearchDropdownContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 오실레이터 검색 드롭다운 Compose UI smoke test.
 *
 * 실행: `./gradlew connectedDebugAndroidTest` (Android 기기/에뮬레이터 필요)
 */
@RunWith(AndroidJUnit4::class)
class StockSearchDropdownSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val samsung = StockMasterEntity(
        ticker = "005930",
        name = "삼성전자",
        market = "KOSPI",
        sector = "전기전자",
        initialConsonants = "ㅅㅅㅈㅈ",
        lastUpdated = 0L
    )

    @Test
    fun 검색어가_비어있을_때_최근_검색_라벨과_종목명이_렌더링된다() {
        composeTestRule.setContent {
            StockSearchDropdownContent(
                searchResults = emptyList(),
                recentSearches = listOf(samsung),
                query = "",
                onStockSelected = {},
                onClearRecent = {},
            )
        }

        composeTestRule.onNodeWithText("최근 검색").assertIsDisplayed()
        composeTestRule.onNodeWithText("삼성전자").assertIsDisplayed()
    }

    @Test
    fun 검색어가_있을_때_결과_종목_클릭_시_콜백이_호출된다() {
        var selected: StockMasterEntity? = null
        composeTestRule.setContent {
            StockSearchDropdownContent(
                searchResults = listOf(samsung),
                recentSearches = emptyList(),
                query = "삼성",
                onStockSelected = { selected = it },
                onClearRecent = {},
            )
        }

        composeTestRule.onNodeWithText("삼성전자").performClick()
        assert(selected?.ticker == "005930") { "onStockSelected callback not invoked" }
    }
}
