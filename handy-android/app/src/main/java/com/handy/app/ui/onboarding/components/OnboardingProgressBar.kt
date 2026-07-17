package com.handy.app.ui.onboarding.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.Spacing

/**
 * Sprint 27a — OnboardingProgressBar.
 *
 * Renders an M3 [LinearProgressIndicator] (clamped progress) with a
 * centered "Downloading N%" label below it. Used by
 * `ModelDownloadContent` for the model-download step.
 *
 * Two pure helpers are exposed `internal` so JVM tests can validate
 * the clamp + percent-formatting contract independently of
 * Compose — see `OnboardingPresentationLogicTest`.
 */
@Composable
fun OnboardingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LinearProgressIndicator(
            progress = { progressFraction(progress) },
            modifier = Modifier.fillMaxWidth(0.7f),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(
                R.string.download_progress_percent,
                labelPercent(progress),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Pure: clamp [progress] into `[0f, 1f]`. Single-input, side-effect-
 * free. Used by `LinearProgressIndicator(progress = …)` (the M3
 * parameter contract accepts a `() -> Float` lambda; we coerce here
 * rather than letting the producer race against undefined values).
 */
internal fun progressFraction(progress: Float): Float = progress.coerceIn(0f, 1f)

/**
 * Pure: format [progress] as an integer percent 0..100. Used by the
 * label sibling of the progress bar. Truncates fractional values
 * (matching the existing pre-Sprint-27 inline `(progress * 100).toInt()`
 * behaviour so seeded migrations don't change labels).
 */
internal fun labelPercent(progress: Float): Int =
    (progress.fractionClamped() * 100f).toInt()

private fun Float.fractionClamped(): Float = coerceIn(0f, 1f)
