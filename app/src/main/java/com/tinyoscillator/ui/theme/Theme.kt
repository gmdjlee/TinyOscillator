package com.tinyoscillator.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

data class FinanceColors(
    val positive: Color,
    val negative: Color,
    val neutral: Color
)

val LocalFinanceColors = staticCompositionLocalOf {
    FinanceColors(
        positive = Positive,
        negative = Negative,
        neutral = Neutral
    )
}

/** Theme mode preference: SYSTEM / LIGHT / DARK */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Composable state holder for theme mode with SharedPreferences persistence */
class ThemeModeState(
    private val context: Context,
    initial: ThemeMode
) {
    var mode by mutableStateOf(initial)
        private set

    fun setThemeMode(newMode: ThemeMode) {
        mode = newMode
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            .edit { putString("theme_mode", newMode.name) }
    }

    companion object {
        fun load(context: Context): ThemeModeState {
            val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
            val mode = try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.SYSTEM }
            return ThemeModeState(context, mode)
        }
    }
}

val LocalThemeModeState = staticCompositionLocalOf<ThemeModeState> {
    error("ThemeModeState not provided")
}

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceBright = LightSurfaceBright,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary
)

@Composable
fun TinyOscillatorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themeModeState = remember { ThemeModeState.load(context) }

    val resolvedDarkTheme = when (themeModeState.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> darkTheme
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (resolvedDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        resolvedDarkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val financeColors = if (resolvedDarkTheme) {
        FinanceColors(
            positive = PositiveDark,
            negative = NegativeDark,
            neutral = NeutralDark
        )
    } else {
        FinanceColors(
            positive = Positive,
            negative = Negative,
            neutral = Neutral
        )
    }

    CompositionLocalProvider(
        LocalFinanceColors provides financeColors,
        LocalThemeModeState provides themeModeState
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
