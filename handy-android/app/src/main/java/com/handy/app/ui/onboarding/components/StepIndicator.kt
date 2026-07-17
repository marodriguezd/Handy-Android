package com.handy.app.ui.onboarding.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.ui.components.HandySpringTokens
import com.handy.app.ui.components.Spacing
import androidx.compose.ui.res.stringResource

/**
 * Sprint 27a — MD3-elevated StepIndicator. The pre-Sprint-27 inline
 * version was a 10dp / 8dp pill row on `surfaceVariant`; the new shape
 * is:
 *   - The entire indicator is wrapped in a `Surface` (M3 primitive)
 *     with `tonalElevation = 3.dp` to bring it forward of the
 *     background and signal "you are progressing through this flow".
 *   - Each dot is visible at 10dp / 8dp but surrounded by a 48dp touch
 *     target so larger fingers / accessibility switch controls can
 *     activate it without precise aim.
 *   - Color transitions are via [animateColorAsState] on the
 *     `primary` / `surfaceVariant` M3 tokens; size is via
 *     [animateDpAsState] (active 10dp → inactive 8dp).
 *   - Gentle spring ([HandySpringTokens.gentle]) drives the size and
 *     color transitions so the indicator lags with a soft settle
 *     rather than snapping.
 *
 * The label `"Step N of M"` lives beneath the row and is the canonical
 * MD3 pattern for multi-step flows (cf. M3 navigation patterns guide).
 *
 * Progress semantics: the "current" indicator lights up the dot for
 * the index ≤ currentStep (1-based). For example currentStep=2 lights
 * the first three dots. The total count is the inherent totalSteps
 * passed in from the ViewModel.
 */
@Composable
fun StepIndicator(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    val safeSteps = totalSteps.coerceAtLeast(1)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(
                Spacing.sm,
                Alignment.CenterHorizontally,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(safeSteps) { index ->
                StepDot(
                    isCompleted = index < currentStep,
                    isActive = index == currentStep,
                )
            }
        }
    }
    StepIndicatorLabel(
        currentStep = currentStep,
        totalSteps = safeSteps,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StepDot(
    isCompleted: Boolean,
    isActive: Boolean,
) {
    val targetSize by animateDpAsState(
        targetValue = if (isActive || isCompleted) 10.dp else 8.dp,
        animationSpec = HandySpringTokens.gentle(),
        label = "step_dot_size",
    )
    val targetScale by animateFloatAsState(
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = HandySpringTokens.gentle(),
        label = "step_dot_scale",
    )
    val targetColor by animateColorAsState(
        targetValue = dotColorFor(isCompleted = isCompleted, isActive = isActive),
        animationSpec = HandySpringTokens.gentle(),
        label = "step_dot_color",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(targetScale),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(targetSize)
                .clip(CircleShape)
                .background(targetColor),
        )
    }
}

@Composable
private fun dotColorFor(
    isCompleted: Boolean,
    isActive: Boolean,
): Color = when {
    isCompleted -> MaterialTheme.colorScheme.primary
    isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun StepIndicatorLabel(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(
            R.string.onboarding_step_label_format,
            (currentStep + 1).coerceAtMost(totalSteps),
            totalSteps,
        ),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = Spacing.xs),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
