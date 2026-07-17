package com.handy.app.ui.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.ui.components.Spacing

/**
 * Sprint 27a — OnboardingButtonRow.
 *
 * Renders the 3-button layout for an onboarding flow:
 *
 *   - "Back"   — `OutlinedButton`, only shown when [currentStep] > 0.
 *   - "Skip"   — `TextButton`, only shown when [onSkip] is non-null
 *                (per-step gating: the Welcome step has no real Skip,
 *                the Model step has a permanent Skip because the user
 *                can defer the model download).
 *   - "Primary"— `Button`, label decided by [primaryLabelRes] which
 *                returns "Get Started" / "Next" / "Start using Handy"
 *                depending on the current step position.
 *
 * All three buttons hit 48dp touch targets per the M3 interaction
 * defaults. Spacing uses [Spacing.sm] between adjacent affordances.
 *
 * The [primaryLabelRes] function is `internal` so its consumers
 * (Compose + JVM tests) live in the same module — see
 * `OnboardingPresentationLogicTest`.
 */
@Composable
fun OnboardingButtonRow(
    currentStep: Int,
    totalSteps: Int,
    onBack: (() -> Unit)?,
    onPrimary: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null && currentStep > 0) {
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.onboarding_back))
            }
        } else {
            // Sentinel spacer so the primary button stays right-aligned
            // when Back is absent (step 0). 1dp is invisible but
            // keeps the SpaceBetween layout balanced.
            Spacer(Modifier.width(1.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onSkip != null) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.onboarding_skip))
                }
                Spacer(Modifier.width(Spacing.sm))
            }
            Button(onClick = onPrimary) {
                Text(
                    stringResource(
                        primaryLabelRes(currentStep = currentStep, totalSteps = totalSteps),
                    ),
                )
            }
        }
    }
}

/**
 * Sprint 27a — Picks the right button-label string for the primary
 * (right-most) button given the current step position:
 *
 *  - step 0           → "Get Started"     (R.string.onboarding_get_started)
 *  - step total-1     → "Start using Handy" (R.string.onboarding_start)
 *  - any other step   → "Next"            (R.string.onboarding_next)
 *
 * Pure function for JVM-testability — see
 * `OnboardingPresentationLogicTest`.
 */
internal fun primaryLabelRes(currentStep: Int, totalSteps: Int): Int {
    val safeTotal = totalSteps.coerceAtLeast(1)
    val isLast = currentStep >= safeTotal - 1
    val isFirst = currentStep <= 0
    return when {
        isLast -> R.string.onboarding_start
        isFirst -> R.string.onboarding_get_started
        else -> R.string.onboarding_next
    }
}
