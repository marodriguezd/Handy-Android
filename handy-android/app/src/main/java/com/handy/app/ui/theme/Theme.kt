package com.handy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HandyColorScheme = darkColorScheme(
    primary = HandyPrimary,
    onPrimary = HandyOnPrimary,
    primaryContainer = HandyPrimary.copy(alpha = 0.2f),
    onPrimaryContainer = HandyOnBackground,
    secondary = HandySecondary,
    onSecondary = HandyOnSecondary,
    secondaryContainer = HandySecondary.copy(alpha = 0.2f),
    onSecondaryContainer = HandyOnBackground,
    background = HandyBackground,
    onBackground = HandyOnBackground,
    surface = HandySurface,
    onSurface = HandyOnSurface,
    surfaceVariant = HandySurfaceVariant,
    onSurfaceVariant = HandyOnSurfaceVariant,
    error = HandyError,
    onError = HandyOnError,
    outline = HandyOutline,
)

@Composable
fun HandyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HandyColorScheme,
        typography = HandyTypography,
        shapes = HandyShapes,
        content = content,
    )
}
