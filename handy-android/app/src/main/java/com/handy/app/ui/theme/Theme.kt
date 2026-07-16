package com.handy.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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
    primary = Color(0xFF9A3C6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E4),
    onPrimaryContainer = Color(0xFF3E0024),
    secondary = Color(0xFF6E5659),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0DEE0),
    onSecondaryContainer = Color(0xFF261A1C),
    tertiary = Color(0xFF815356),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDADC),
    onTertiaryContainer = Color(0xFF331019),
    background = Color(0xFFFDFBFB),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFDFBFB),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFECE0E1),
    onSurfaceVariant = Color(0xFF4A4546),
    inverseSurface = Color(0xFF2C2B29),
    inverseOnSurface = Color(0xFFFDFBFB),
    inversePrimary = Color(0xFFFFAFD0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF7D7475),
    outlineVariant = Color(0xFFD6C3C5),
    scrim = Color(0xFF000000),
)

@Composable
fun HandyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
