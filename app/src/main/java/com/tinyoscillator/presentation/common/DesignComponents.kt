package com.tinyoscillator.presentation.common

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tinyoscillator.ui.theme.LocalExtendedColors
import com.tinyoscillator.ui.theme.ThemeMode
import com.tinyoscillator.ui.theme.ThemeModeState
import kotlin.math.sin

/**
 * Jade Terminal 디자인 시스템.
 *
 * - FinanceCard: 비대칭 코너 + 미묘한 그라디언트 보더
 * - PillTabRow / ScrollablePillTabRow: 스프링 애니메이션 + 인디케이터 바
 * - GlassCard: 유리형 반투명 + 블러
 * - GeometricBackground: 캔버스 기반 기하학적 패턴 배경
 * - GrainOverlay: 노이즈 텍스처 오버레이
 */

// ═══════════════════════════════════════════════════════════════
// Jade Terminal Card — 비대칭 코너 + 미묘한 그라디언트 보더
// ═══════════════════════════════════════════════════════════════

/** 비대칭 코너 라운드: 좌상단 20dp, 우하단 20dp, 나머지 6dp */
private val AsymmetricCardShape = RoundedCornerShape(
    topStart = 20.dp,
    topEnd = 6.dp,
    bottomEnd = 20.dp,
    bottomStart = 6.dp
)

@Composable
fun FinanceCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
        )
    )

    // borderColor 지정 시 활성/경고 강조용 단색 Stroke, 없으면 기존 미묘한 그라디언트 보더
    val borderModifier = Modifier.drawBehind {
        if (borderColor != null) {
            drawRoundRect(
                color = borderColor,
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        } else {
            drawRoundRect(
                brush = borderBrush,
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }

    val cardColors = CardDefaults.cardColors(containerColor = containerColor)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = elevation)
    val innerContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content
        )
    }

    if (onClick != null) {
        // 클릭 가능 오버로드 — 리플이 비대칭 shape에 클립된다
        Card(
            onClick = onClick,
            modifier = modifier.then(borderModifier),
            shape = AsymmetricCardShape,
            colors = cardColors,
            elevation = cardElevation,
            content = innerContent
        )
    } else {
        Card(
            modifier = modifier.then(borderModifier),
            shape = AsymmetricCardShape,
            colors = cardColors,
            elevation = cardElevation,
            content = innerContent
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Pill Tab Row — 스프링 애니메이션
// ═══════════════════════════════════════════════════════════════

@Composable
fun <T> PillTabRow(
    tabs: List<T>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    tabLabel: (T) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedTab == tab
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "tabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
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

// ═══════════════════════════════════════════════════════════════
// Scrollable Pill Tab Row — 밑줄 인디케이터 + 스프링
// ═══════════════════════════════════════════════════════════════

@Composable
fun <T> ScrollablePillTabRow(
    tabs: List<T>,
    selectedTab: T,
    onTabSelected: (T) -> Unit,
    tabLabel: (T) -> String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedTab == tab
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerLow,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "scrollTabBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "scrollTabText"
            )
            // 선택 시 약간 확대되는 스프링 효과
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.95f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "tabScale"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer(scaleX = scale, scaleY = scale)
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

// ═══════════════════════════════════════════════════════════════
// Section Header
// ═══════════════════════════════════════════════════════════════

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 긴 제목/부제가 Row 전체 폭을 차지하면 action 슬롯이 0dp로 압살된다 —
        // weight(fill=false)로 좌측 텍스트가 남은 폭 안에서만 줄바꿈하도록 제한.
        Column(
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

// ═══════════════════════════════════════════════════════════════
// Accordion Card — 14dp 균일 라운드 + 접기/펼치기 (T9 BearSignal 폰 아코디언 · T11 설정 재사용)
// ═══════════════════════════════════════════════════════════════

/**
 * 접이식 섹션 카드 — 헤더(제목·부제·셰브런)를 탭하면 본문이 펼쳐진다.
 *
 * FinanceCard의 비대칭 코너와 달리 14dp 균일 라운드(시안 확정)를 쓴다 — 목록형으로 여러 개가
 * 세로로 쌓일 때 비대칭 코너가 리듬을 깨기 때문. 폰(COMPACT)에서 세부 섹션을 접어 스크롤 길이를
 * 줄이는 용도이며, 후속 T11 설정 화면에서도 재사용할 수 있도록 BearSignal 전용 상태를 참조하지
 * 않는다(순수 표시 컴포넌트 — [expanded]/[onToggle]은 호출측이 소유).
 */
@Composable
fun AccordionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "accordionChevron"
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "접기" else "펼치기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                exit = shrinkVertically(tween(240)) + fadeOut(tween(180))
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    content()
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Glass Card — 유리형 반투명 + 블러 + 제이드 글로우
// ═══════════════════════════════════════════════════════════════

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val extended = LocalExtendedColors.current
    val gradientOverlay = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            Color.Transparent,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )

    Card(
        modifier = modifier,
        shape = AsymmetricCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(16.dp)
            } else {
                Modifier
            }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .background(gradientOverlay)
            )
        }
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Carved Text Field — 어두운 배경 + 제이드 포커스 보더
// ═══════════════════════════════════════════════════════════════

@Composable
fun CarvedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    maxLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// ═══════════════════════════════════════════════════════════════
// Category Badge — 제이드/골드/로즈 뱃지
// ═══════════════════════════════════════════════════════════════

@Composable
fun CategoryBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Theme Toggle Icon
// ═══════════════════════════════════════════════════════════════

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

// ═══════════════════════════════════════════════════════════════
// Geometric Background — 캔버스 기반 미묘한 기하학적 그리드
// ═══════════════════════════════════════════════════════════════

/**
 * 화면 배경에 미묘한 기하학적 점 그리드를 그리는 Modifier.
 * 다크 모드에서 미세한 빛점, 라이트 모드에서 미세한 어두운 점.
 */
fun Modifier.geometricGridBackground(
    dotColor: Color,
    spacing: Float = 48f
): Modifier = this.drawBehind {
    val cols = (size.width / spacing).toInt() + 1
    val rows = (size.height / spacing).toInt() + 1
    for (row in 0..rows) {
        for (col in 0..cols) {
            val offsetX = col * spacing + if (row % 2 == 0) 0f else spacing / 2
            drawCircle(
                color = dotColor,
                radius = 1f,
                center = Offset(offsetX, row * spacing)
            )
        }
    }
}

/**
 * 미묘한 웨이브 라인 배경 — 먹물 번짐(수묵) 느낌.
 */
@Composable
fun InkWashBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val waveCount = 3
            for (i in 0 until waveCount) {
                val yBase = size.height * (0.3f + i * 0.2f)
                val path = Path().apply {
                    moveTo(0f, yBase)
                    var x = 0f
                    while (x < size.width) {
                        val y = yBase + sin((x / size.width * Math.PI * 2 + i).toDouble()).toFloat() * 30f
                        lineTo(x, y)
                        x += 2f
                    }
                }
                drawPath(
                    path = path,
                    color = primary.copy(alpha = 0.03f - i * 0.008f),
                    style = Stroke(width = 1.5f)
                )
            }
        }
        content()
    }
}
