package com.tinyoscillator

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 기기에서 Compose UI Test 인프라(createComposeRule, text selectors)가
 * 올바르게 동작하는지를 확인하는 최소 smoke test.
 *
 * 다른 androidTest가 실패하면 이 테스트가 통과하는지 먼저 확인해 인프라 문제와
 * 화면별 문제를 분리한다.
 *
 * 실행: `./gradlew connectedDebugAndroidTest` (Android 기기/에뮬레이터 필요)
 */
@RunWith(AndroidJUnit4::class)
class ComposeInfraSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun compose_test_rule이_텍스트를_셀렉트할_수_있다() {
        composeTestRule.setContent {
            Text(text = "hello-compose-test")
        }

        composeTestRule.onNodeWithText("hello-compose-test").assertIsDisplayed()
    }
}
