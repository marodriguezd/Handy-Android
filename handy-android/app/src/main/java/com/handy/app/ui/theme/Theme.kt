package com.handy.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext

/** Theme mode persisted in `SettingsStore`.  Lives in the platform-neutral
 *  [`ui.theme`] package because it has no Compose dependency, but the
 *  package was preserved for backward-compatibility with downstream callers. */
enum class ThemeMode { System, Light, Dark }

/**
 * Material Design 3 dark color scheme for Handy — full tonal hierarchy.
 * Tokens come from `ui/theme/Color.kt` and are derived from the Handy PC
 * palette (background #2c2b29, text #fbfbfb, logo primary #f28cbb).
 *
 * The M3 Expressive "Fixed" tokens (`primaryFixed*`, `secondaryFixed*`,
 * `tertiaryFixed*`) were intentionally omitted.  They live in Color.kt
 * for future M3 1.4+ rollouts but the current Kotlin toolchain's
 * resolved Material3 surface doesn't expose them on `darkColorScheme(...)`,
 * so we drop the references here to keep the project green.
 */
private val HandyDarkColorScheme = darkColorScheme(
    primary = HandyPrimary,
    onPrimary = HandyOnPrimary,
    primaryContainer = HandyPrimaryContainer,
    onPrimaryContainer = HandyOnPrimaryContainer,

    secondary = HandySecondary,
    onSecondary = HandyOnSecondary,
    secondaryContainer = HandySecondaryContainer,
    onSecondaryContainer = HandyOnSecondaryContainer,

    tertiary = HandyTertiary,
    onTertiary = HandyOnTertiary,
    tertiaryContainer = HandyTertiaryContainer,
    onTertiaryContainer = HandyOnTertiaryContainer,

    background = HandyBackground,
    onBackground = HandyOnBackground,

    surface = HandySurface,
    onSurface = HandyOnSurface,
    surfaceVariant = HandySurfaceVariant,
    onSurfaceVariant = HandyOnSurfaceVariant,
    surfaceTint = HandyPrimary,
    surfaceBright = HandySurfaceBright,
    surfaceDim = HandySurfaceDim,
    surfaceContainerLowest = HandySurfaceContainerLowest,
    surfaceContainerLow = HandySurfaceContainerLow,
    surfaceContainer = HandySurfaceContainer,
    surfaceContainerHigh = HandySurfaceContainerHigh,
    surfaceContainerHighest = HandySurfaceContainerHighest,

    inverseSurface = HandyInverseSurface,
    inverseOnSurface = HandyInverseOnSurface,
    inversePrimary = HandyInversePrimary,

    error = HandyError,
    onError = HandyOnError,
    errorContainer = HandyErrorContainer,
    onErrorContainer = HandyOnErrorContainer,

    outline = HandyOutline,
    outlineVariant = HandyOutlineVariant,
    scrim = HandyScrim,
)

private val HandyLightColorScheme = lightColorScheme(
    primary = HandyLightPrimary,
    onPrimary = HandyLightOnPrimary,
    primaryContainer = HandyLightPrimaryContainer,
    onPrimaryContainer = HandyLightOnPrimaryContainer,

    secondary = HandyLightSecondary,
    onSecondary = HandyLightOnSecondary,
    secondaryContainer = HandyLightSecondaryContainer,
    onSecondaryContainer = HandyLightOnSecondaryContainer,

    tertiary = HandyLightTertiary,
    onTertiary = HandyLightOnTertiary,
    tertiaryContainer = HandyLightTertiaryContainer,
    onTertiaryContainer = HandyLightOnTertiaryContainer,

    background = HandyLightBackground,
    onBackground = HandyLightOnBackground,

    surface = HandyLightSurface,
    onSurface = HandyLightOnSurface,
    surfaceVariant = HandyLightSurfaceVariant,
    onSurfaceVariant = HandyLightOnSurfaceVariant,
    surfaceTint = HandyLightPrimary,
    surfaceBright = HandyLightSurfaceBright,
    surfaceDim = HandyLightSurfaceDim,
    surfaceContainerLowest = HandyLightSurfaceContainerLowest,
    surfaceContainerLow = HandyLightSurfaceContainerLow,
    surfaceContainer = HandyLightSurfaceContainer,
    surfaceContainerHigh = HandyLightSurfaceContainerHigh,
    surfaceContainerHighest = HandyLightSurfaceContainerHighest,

    inverseSurface = HandyLightInverseSurface,
    inverseOnSurface = HandyLightInverseOnSurface,
    inversePrimary = HandyLightInversePrimary,

    error = HandyLightError,
    onError = HandyLightOnError,
    errorContainer = HandyLightErrorContainer,
    onErrorContainer = HandyLightOnErrorContainer,

    outline = HandyLightOutline,
    outlineVariant = HandyLightOutlineVariant,
    scrim = HandyLightScrim,
)

/**
 * HandyTheme — Material Design 3 composer that owns colorScheme + typography +
 * shapes.  Reads user-forced light/dark via [themeModeState] (persisted in
 * `SettingsStore.themeMode`) and Android 12+ dynamic color via
 * [dynamicColorState].
 *
 * Both parameters accept Compose `State<T>` so that user changes from the
 * Theme/Language/About screen recompose the tree without an Activity restart.
 */
@Composable
fun HandyTheme(
    themeModeState: State<ThemeMode>,
    dynamicColorState: State<Boolean>,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val themeMode by themeModeState
    val useDynamicColor by dynamicColorState
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> HandyDarkColorScheme
        else -> HandyLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HandyTypography,
        shapes = HandyShapes,
        content = content,
    )
}
