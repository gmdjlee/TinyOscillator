package com.tinyoscillator.presentation.etf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tinyoscillator.presentation.common.KrxCredentialDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ETF 화면에서 사용하는 KRX 자격증명 입력 다이얼로그 smoke test.
 *
 * 실행: `./gradlew connectedDebugAndroidTest` (Android 기기/에뮬레이터 필요)
 */
@RunWith(AndroidJUnit4::class)
class KrxCredentialDialogSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun 다이얼로그_타이틀과_description이_표시된다() {
        val description = "ETF 데이터 수집을 위해 KRX 데이터시스템 계정이 필요합니다."

        composeTestRule.setContent {
            KrxCredentialDialog(
                description = description,
                onDismiss = {},
                onSave = {},
            )
        }

        composeTestRule.onNodeWithText("KRX 로그인 정보").assertIsDisplayed()
        composeTestRule.onNodeWithText(description).assertIsDisplayed()
        composeTestRule.onNodeWithText("KRX ID").assertIsDisplayed()
        composeTestRule.onNodeWithText("비밀번호").assertIsDisplayed()
        composeTestRule.onNodeWithText("나중에").assertIsDisplayed()
        composeTestRule.onNodeWithText("저장").assertIsDisplayed()
    }
}
