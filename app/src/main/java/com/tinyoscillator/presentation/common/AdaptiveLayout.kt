package com.tinyoscillator.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 화면 너비에 따라 단일/이중 패널 레이아웃을 자동 선택하는 Adaptive 레이아웃.
 * 폴더블 스마트폰 언폴드 상태(~600dp+)에서 Two-Pane으로 전환.
 */
enum class WindowType { COMPACT, MEDIUM, EXPANDED }

fun calculateWindowType(widthDp: Dp): WindowType = when {
    widthDp < 600.dp -> WindowType.COMPACT
    widthDp < 840.dp -> WindowType.MEDIUM
    else -> WindowType.EXPANDED
}

/**
 * Two-Pane 레이아웃: MEDIUM 이상에서 좌/우 패널 분할.
 * @param listPaneWeight 좌측 패널 비율 (0.0~1.0)
 * @param listPane 좌측 패널 콘텐츠 (검색, 필터, 목록 등)
 * @param detailPane 우측 패널 콘텐츠 (차트, 상세정보 등)
 * @param singlePane COMPACT 모드에서의 단일 패널 콘텐츠
 */
@Composable
fun TwoPaneLayout(
    windowType: WindowType,
    listPaneWeight: Float = 0.38f,
    listPane: @Composable () -> Unit,
    detailPane: @Composable () -> Unit,
    singlePane: @Composable () -> Unit
) {
    if (windowType == WindowType.COMPACT) {
        Box(modifier = Modifier.fillMaxSize()) {
            singlePane()
        }
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(listPaneWeight)
                    .fillMaxHeight()
            ) {
                listPane()
            }
            VerticalDivider(
                modifier = Modifier.fillMaxHeight(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Box(
                modifier = Modifier
                    .weight(1f - listPaneWeight)
                    .fillMaxHeight()
            ) {
                detailPane()
            }
        }
    }
}
