package com.tinyoscillator.presentation.investorprofile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tinyoscillator.domain.model.InvestorProfileQuery
import com.tinyoscillator.domain.model.InvestorType
import com.tinyoscillator.domain.model.ProfileDisplayMode
import com.tinyoscillator.domain.model.ProfilePeriod
import com.tinyoscillator.ui.theme.TinyOscillatorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 주체별 매물대 본문(상태 없는 [InvestorProfileBody]) 렌더링 smoke test.
 * 명세: `docs/TASK_investor_volume_profile.md` §9.4.
 *
 * 차트 자체는 MPAndroidChart `AndroidView`라 Compose 셀렉터로 세부 내용을 검증할 수 없으므로
 * 주체 칩·표기 라인·차트 testTag 존재만 확인한다(형제 smoke `DemarkTDChartSmokeTest` 관례).
 *
 * 실행: `./gradlew connectedDebugAndroidTest` (Android 기기/에뮬레이터 필요)
 */
@RunWith(AndroidJUnit4::class)
class InvestorProfileSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun investorChipsChartAndDisclaimerAreDisplayed() {
        composeTestRule.setContent {
            TinyOscillatorTheme {
                InvestorProfileBody(
                    state = InvestorProfileState.Success(previewProfile()),
                    query = InvestorProfileQuery(period = ProfilePeriod.M6, binCount = 10),
                    selectedInvestors = InvestorType.entries.toSet(),
                    displayMode = ProfileDisplayMode.GROUPED,
                    onToggleInvestor = {},
                    onPeriodSelected = {},
                    onCustomRangeConfirmed = { _, _ -> },
                    onBasisSelected = {},
                    onBinCountSelected = {},
                    onDisplayModeSelected = {},
                    onRetry = {}
                )
            }
        }

        // "개인"은 주체 칩과 지표 카드 행 두 곳에 나타나므로 첫 노드(칩)만 검사한다.
        composeTestRule.onAllNodesWithText("개인", substring = true).onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithTag("InvestorProfileChart").assertExists()
        composeTestRule.onNodeWithText("주체별 매물대는 일별 매매대금에서 산출한 추정치입니다.").assertIsDisplayed()
    }
}
