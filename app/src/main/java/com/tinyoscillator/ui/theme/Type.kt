package com.tinyoscillator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.tinyoscillator.R

/**
 * Jade Terminal 타이포그래피 시스템:
 *
 * - Display/Headline/Title: **Syne** — 기하학적, 미래적, 독특한 리듬
 *   (Google Fonts downloadable, 번들 폰트 폴백)
 * - Body/Label: **DM Sans** — 따뜻한 기하학적, 고밀도 데이터 가독성
 *   (Google Fonts downloadable, 번들 폰트 폴백)
 *
 * letterSpacing 를 적극 조정하여 독특한 시각적 리듬을 만듦.
 * Display: 타이트한 -0.5sp, Body: 여유로운 0.3sp
 */

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val syneFont = GoogleFont("Syne")
private val dmSansFont = GoogleFont("DM Sans")

// Syne: 기하학적 Display/Headline 폰트 (번들 Manrope 폴백)
val SyneFamily = FontFamily(
    Font(googleFont = syneFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = syneFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = syneFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = syneFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = syneFont, fontProvider = fontProvider, weight = FontWeight.ExtraBold),
    // 번들 폴백
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

// DM Sans: 따뜻한 기하학적 Body 폰트 (번들 Inter 폴백)
val DmSansFamily = FontFamily(
    Font(googleFont = dmSansFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = dmSansFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = dmSansFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    // 번들 폴백
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
)

val AppTypography = Typography(
    // ── Display: 대형 숫자/헤드라인, 타이트한 자간 ──
    displayLarge = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.5).sp   // 극도로 타이트
    ),
    displayMedium = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.0).sp
    ),
    displaySmall = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    // ── Headline: 섹션 타이틀, 약간 타이트 ──
    headlineLarge = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.1).sp
    ),
    // ── Title: 카드/섹션 헤더, Syne의 캐릭터 살림 ──
    titleLarge = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp
    ),
    // ── Body: 데이터 표시, 읽기 편한 DM Sans ──
    bodyLarge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.3.sp    // 약간 여유로운 자간
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp
    ),
    // ── Label: 탭, 뱃지, 버튼 — DM Sans Medium ──
    labelLarge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp    // 라벨은 넉넉한 자간
    ),
    labelMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)
