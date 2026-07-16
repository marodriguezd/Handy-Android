package com.handy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Status indicator dot.  Drop-in replacement for the ad-hoc `.background(
 * Color.Red)` shizuku LED and similar status pills.  Picks the right
 * MD3 semantic color from [MaterialTheme.colorScheme] so it follows
 * dark/light + dynamic color changes automatically.
 *
 * @param status: which semantic role to render.  Status corresponds to
 *   a theme token, not a raw `Color(...)`.
 * @param size: dot diameter.  Defaults to 12 dp which matches the
 *   legacy Shizuku indicator; bump to 16 for empty-state summaries.
 */
@Composable
fun StatusDot(
    status: Status,
    modifier: Modifier = Modifier,
    size: Dp = 12.dp,
) {
    val tint: Color = when (status) {
        Status.Success -> MaterialTheme.colorScheme.tertiary
        Status.Warning -> MaterialTheme.colorScheme.secondary
        Status.Error -> MaterialTheme.colorScheme.error
        Status.Info -> MaterialTheme.colorScheme.primary
        Status.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint),
    )
}

enum class Status { Success, Warning, Error, Info, Neutral }
