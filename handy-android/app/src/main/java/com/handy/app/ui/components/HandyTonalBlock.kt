package com.handy.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * MD3-native tonal surface wrapper.  Each [TonalElevation] maps to its
 * matching M3 `surfaceContainer*` token **and** its matching tonal
 * elevation dp.  Together they encode the spec's surface-step hierarchy:
 * cards at "High" sit visibly above "Container"-tinted parents, etc.
 *
 * Used everywhere we used to hard-code `surfaceVariant.copy(alpha = 0.7f)`:
 * the IME pill (Sprint 21), popup anchors, badges-in-cards, animated panel
 * inserts.
 */
@Composable
@Suppress("ModifierParameter")
fun HandyTonalBlock(
    level: TonalElevation = TonalElevation.Container,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val (color: Color, elevation: Dp) = when (level) {
        TonalElevation.Lowest -> MaterialTheme.colorScheme.surfaceContainerLowest to 0.dp
        TonalElevation.Low -> MaterialTheme.colorScheme.surfaceContainerLow to 1.dp
        TonalElevation.Container -> MaterialTheme.colorScheme.surfaceContainer to 3.dp
        TonalElevation.High -> MaterialTheme.colorScheme.surfaceContainerHigh to 6.dp
        TonalElevation.Highest -> MaterialTheme.colorScheme.surfaceContainerHighest to 8.dp
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        tonalElevation = elevation,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        content()
    }
}

/** Convenience: full-width tonal card with optional click handler. */
@Composable
@Suppress("ModifierParameter")
fun HandyTonalCard(
    modifier: Modifier = Modifier,
    level: TonalElevation = TonalElevation.Container,
    shape: Shape = MaterialTheme.shapes.large,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = if (onClick != null && enabled) modifier.clickable(onClick = onClick) else modifier
    HandyTonalBlock(
        modifier = base.fillMaxWidth(),
        level = level,
        shape = shape,
        content = content,
    )
}

enum class TonalElevation { Lowest, Low, Container, High, Highest }
