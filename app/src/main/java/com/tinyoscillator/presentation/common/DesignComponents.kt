package com.tinyoscillator.presentation.common

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tinyoscillator.ui.theme.ThemeMode
import com.tinyoscillator.ui.theme.ThemeModeState

/**
 * Stitch 디자인 시스템 기반 공통 컴포넌트.
 * - 카드: 12dp 라운드, 미세한 그림자, 보더 없음
 * - 탭: Pill 스타일 선택 인디케이터
 * - TopAppBar: 깔끔한 타이틀 + 액션
 */

/**
 * Stitch 스타일 카드: 12dp 라운드 코너, Tonal Layering.
 * Dark: surfaceContainerHigh (#262A31) — "No-Line Rule" 에 따라 배경색 차이로 깊이감 표현
 * Light: surface (#FFFFFF) + 미세 그림자
 */
@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 2.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color.Black.copy(alpha = 0.05f),
                spotColor = Color.Black.copy(alpha = 0.03f)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/** Stitch 스타일 Pill 탭 Row */
@Composable
fun <T> PillTabRow(
    tabs: List<T>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    tabLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedTab == tab
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondary
                else Color.Transparent,
                animationSpec = tween(200),
                label = "tabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "tabText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabLabel(tab),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
            }
        }
    }
}

/** 스크롤 가능한 Pill 탭 (5개 이상 탭용) */
@Composable
fun <T> ScrollablePillTabRow(
    tabs: List<T>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    tabLabel: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedTab == tab
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = tween(200),
                label = "scrollTabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(200),
                label = "scrollTabText"
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabLabel(tab),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor
                )
            }
        }
    }
}

/** Stitch 스타일 섹션 헤더 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        action?.invoke()
    }
}

/**
 * Glassmorphism 카드: 반투명 배경 + 블러 + 그라디언트 오버레이.
 * Android 12+ 에서는 blur 효과 적용, 이하에서는 반투명 배경만 사용.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val glassColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val gradientOverlay = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        )
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    Modifier.blur(20.dp)
                else Modifier
            )
    ) {
        // 블러 배경 레이어 (API 31 미만에서는 단순 반투명)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(glassColor)
                .background(gradientOverlay)
        )
    }
    // 실제 콘텐츠는 블러 없이 렌더링
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            // 그라디언트 오버레이
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(gradientOverlay)
            )
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * Carved 스타일 입력 필드: 어두운 배경(surfaceContainerLowest)에 ghost border.
 * Obsidian Ledger 디자인 시스템의 "Carved Look" 구현.
 */
@Composable
fun CarvedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** 카테고리 뱃지 (ETF 테마 태그 등) */
@Composable
fun CategoryBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/** 라이트/다크/시스템 테마 순환 토글 아이콘 */
@Composable
fun ThemeToggleIcon(themeModeState: ThemeModeState) {
    IconButton(onClick = {
        val next = when (themeModeState.mode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        themeModeState.setThemeMode(next)
    }) {
        Icon(
            imageVector = when (themeModeState.mode) {
                ThemeMode.LIGHT -> Icons.Default.LightMode
                ThemeMode.DARK -> Icons.Default.DarkMode
                ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
            },
            contentDescription = when (themeModeState.mode) {
                ThemeMode.LIGHT -> "라이트 모드"
                ThemeMode.DARK -> "다크 모드"
                ThemeMode.SYSTEM -> "시스템 설정"
            }
        )
    }
}
