package com.tinyoscillator.presentation.demark

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tinyoscillator.domain.model.DemarkPeriodType
import com.tinyoscillator.domain.model.DemarkTDChartData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DeMark TD Chart 헤더 타이틀 렌더링 smoke test.
 *
 * 차트 자체는 MPAndroidChart `AndroidView`라 Compose 셀렉터로 검증할 수 없으므로
 * Composable 래퍼가 올바른 제목 텍스트(종목명 + 기간 라벨)을 렌더하는지만 확인한다.
 *
 * 실행: `./gradlew connectedDebugAndroidTest` (Android 기기/에뮬레이터 필요)
 */
@RunWith(AndroidJUnit4::class)
class DemarkTDChartSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun 차트_타이틀에_종목명과_기간_라벨이_포함된다() {
        val chartData = DemarkTDChartData(
            stockName = "삼성전자",
            ticker = "005930",
            rows = emptyList(),
            periodType = DemarkPeriodType.DAILY,
        )

        composeTestRule.setContent {
            DemarkTDChart(chartData = chartData)
        }

        composeTestRule.onNodeWithText("삼성전자 DeMark TD (일봉)").assertIsDisplayed()
    }
}
