package com.tinyoscillator.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// Jade Terminal — 청자 터미널
// Korean celadon pottery meets modern trading terminal.
// Dark-first design: ink-blue darks, jade green primary,
// brass gold secondary, plum rose tertiary.
// Light: warm hanji (한지) paper tones.
// ═══════════════════════════════════════════════════════════════

// === Dark Theme (Ink Terminal — 먹빛 터미널) ===
val DarkPrimary = Color(0xFF6ECBA8)            // Celadon jade
val DarkOnPrimary = Color(0xFF003828)
val DarkPrimaryContainer = Color(0xFF1A4D3A)
val DarkOnPrimaryContainer = Color(0xFFB8F0D8)

val DarkSecondary = Color(0xFFE8C36A)          // Brass gold
val DarkOnSecondary = Color(0xFF3D2E00)
val DarkSecondaryContainer = Color(0xFF4D3D10)
val DarkOnSecondaryContainer = Color(0xFFF5E4A8)

val DarkTertiary = Color(0xFFD4899E)           // Plum blossom
val DarkOnTertiary = Color(0xFF3E1028)
val DarkTertiaryContainer = Color(0xFF5A2840)
val DarkOnTertiaryContainer = Color(0xFFF5C8D8)

val DarkError = Color(0xFFFF6B6B)
val DarkOnError = Color(0xFF4A0000)
val DarkErrorContainer = Color(0xFF6B1A1A)
val DarkOnErrorContainer = Color(0xFFFFD5D5)

val DarkBackground = Color(0xFF0B0E14)          // Deep ink
val DarkOnBackground = Color(0xFFE2DED5)
val DarkSurface = Color(0xFF0B0E14)
val DarkOnSurface = Color(0xFFE2DED5)
val DarkSurfaceVariant = Color(0xFF2A3040)
val DarkOnSurfaceVariant = Color(0xFFA8A4A0)
val DarkOutline = Color(0xFF3E4555)
val DarkOutlineVariant = Color(0xFF252A35)

// Ink Terminal: Tonal Surface Container Hierarchy
val DarkSurfaceContainer = Color(0xFF151820)
val DarkSurfaceContainerLow = Color(0xFF10131A)
val DarkSurfaceContainerHigh = Color(0xFF1C2030)
val DarkSurfaceContainerHighest = Color(0xFF252A38)
val DarkSurfaceContainerLowest = Color(0xFF080A0F)
val DarkSurfaceBright = Color(0xFF303848)
val DarkSurfaceDim = Color(0xFF0B0E14)
val DarkInverseSurface = Color(0xFFE2DED5)
val DarkInverseOnSurface = Color(0xFF2A2720)
val DarkInversePrimary = Color(0xFF1A6B4D)

// === Light Theme (Hanji Warm — 한지 밝음) ===
val LightPrimary = Color(0xFF1A6B4D)           // Deep celadon
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFC0E8D5)
val LightOnPrimaryContainer = Color(0xFF0A3520)

val LightSecondary = Color(0xFF7A5A10)         // Polished brass
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFF0DCA0)
val LightOnSecondaryContainer = Color(0xFF3D2E00)

val LightTertiary = Color(0xFF884058)          // Dried plum
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF5C8D8)
val LightOnTertiaryContainer = Color(0xFF3E1028)

val LightError = Color(0xFFC42B2B)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFD5D5)
val LightOnErrorContainer = Color(0xFF6B1A1A)

val LightBackground = Color(0xFFF5EFE3)        // Hanji paper
val LightOnBackground = Color(0xFF1A1710)
val LightSurface = Color(0xFFF5EFE3)
val LightOnSurface = Color(0xFF1A1710)
val LightSurfaceVariant = Color(0xFFE0D8C8)
val LightOnSurfaceVariant = Color(0xFF504A3A)
val LightOutline = Color(0xFFC0B8A5)
val LightOutlineVariant = Color(0xFFD8D0C0)
val LightSurfaceContainer = Color(0xFFECE6DA)
val LightSurfaceContainerLow = Color(0xFFF0EAE0)
val LightSurfaceContainerHigh = Color(0xFFE5DED0)
val LightSurfaceContainerHighest = Color(0xFFDDD5C5)
val LightSurfaceContainerLowest = Color(0xFFFFFDF5)
val LightSurfaceBright = Color(0xFFF5EFE3)
val LightSurfaceDim = Color(0xFFD5CFC3)
val LightInverseSurface = Color(0xFF322F28)
val LightInverseOnSurface = Color(0xFFF0EAE0)
val LightInversePrimary = Color(0xFF6ECBA8)

// === Semantic Colors (Korean Market Finance) ===
// Korean convention: Red/warm = up, Blue/cool = down
val Positive = Color(0xFFD05540)       // Cinnabar red (상승)
val Negative = Color(0xFF4088CC)       // Clear blue (하락)
val Neutral = Color(0xFF8A8580)        // Stone gray

val PositiveDark = Color(0xFFEF7B68)   // Warm cinnabar
val NegativeDark = Color(0xFF68B0F0)   // Sky blue
val NeutralDark = Color(0xFFA8A4A0)    // Muted stone

// === Extended Palette (Charts, Badges, Accents) ===
val JadeGlow = Color(0xFF3DD9A0)       // Bright jade for highlights
val BrassShimmer = Color(0xFFF5D060)   // Bright gold for emphasis
val InkWash = Color(0xFF1A1E2A)        // Subtle ink wash overlay
val HanjiCream = Color(0xFFFAF5E8)     // Warm cream for elevated light surfaces
