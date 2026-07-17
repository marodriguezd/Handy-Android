package com.handy.app.ui.onboarding

import com.handy.app.R
import com.handy.app.ui.onboarding.components.labelPercent
import com.handy.app.ui.onboarding.components.primaryLabelRes
import com.handy.app.ui.onboarding.components.progressFraction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sprint 27a — 5 JVM tests covering the pure presentation helpers
 * extracted from Sprint 27a's Onboarding component sweep.
 *
 * All three helpers are `internal` so they live in the same module as
 * the production source. Pure JVM (no Robolectric, no Compose deps).
 *
 * Test surface:
 *  - [progressFraction] boundary clamping (3 cases).
 *  - [labelPercent]     percent formatting + clamp (3 cases).
 *  - [primaryLabelRes]  step-position → label-resource mapping
 *                      (3 cases: first / middle / last).
 */
class OnboardingPresentationLogicTest {

    @Test
    fun `progressFraction clamps below zero to zero`() {
        assertEquals(0f, progressFraction(-0.5f), 0.0001f)
        assertEquals(0f, progressFraction(-100f), 0.0001f)
    }

    @Test
    fun `progressFraction clamps above one to one`() {
        assertEquals(1f, progressFraction(1.5f), 0.0001f)
        assertEquals(1f, progressFraction(100f), 0.0001f)
    }

    @Test
    fun `progressFraction leaves in-range values untouched`() {
        assertEquals(0f, progressFraction(0f), 0.0001f)
        assertEquals(0.5f, progressFraction(0.5f), 0.0001f)
        assertEquals(1f, progressFraction(1f), 0.0001f)
    }

    @Test
    fun `labelPercent formats as int and clamps out-of-range input`() {
        assertEquals(0, labelPercent(-0.5f))
        assertEquals(50, labelPercent(0.5f))
        assertEquals(100, labelPercent(1f))
        // Above-range saturates to 100, matching progressFraction
        // semantics.
        assertEquals(100, labelPercent(1.7f))
        // Sub-percent fractional truncation: 0.333 * 100 = 33.3 → 33.
        assertEquals(33, labelPercent(0.333f))
    }

    @Test
    fun `primaryLabelRes picks first-middle-last by step position`() {
        // Step 0 with 5 total → "Get Started".
        assertEquals(R.string.onboarding_get_started, primaryLabelRes(0, 5))
        // Step 4 (last) with 5 total → "Start using Handy".
        assertEquals(R.string.onboarding_start, primaryLabelRes(4, 5))
        // Step 2 (middle) with 5 total → "Next".
        assertEquals(R.string.onboarding_next, primaryLabelRes(2, 5))

        // Edge: totalSteps = 1 collapses isFirst/isLast to the same
        // step; both flags fire. `isLast` is checked first above so
        // the label is "Start using Handy".
        assertEquals(R.string.onboarding_start, primaryLabelRes(0, 1))
        // Edge: totalSteps = 0 coerced to 1 by safeTotal.
        assertEquals(R.string.onboarding_start, primaryLabelRes(0, 0))
        // Edge: currentStep < 0 (defensive contract for an out-of-
        // range producer) collapses isFirst to true → "Get Started".
        assertEquals(R.string.onboarding_get_started, primaryLabelRes(-1, 5))
    }
}
