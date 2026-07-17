package com.handy.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 29b — JUnit4 mirror test for [PredictiveBackPresentation].
 *
 * Locks the predicate that gates the root-level
 * [androidx.activity.compose.PredictiveBackHandler] in
 * [AppNavigation]. Three states must be distinguished:
 *
 *  1. `currentRoute == null` — initial composition, no destination yet.
 *     Handler should be disabled (no flow events would arrive anyway,
 *     but the predicate must agree to keep the contract clean).
 *  2. `currentRoute == startRoute` — back-press should exit the app,
 *     not pop the back stack. Handler disabled.
 *  3. `currentRoute != null && currentRoute != startRoute` — normal
 *     back-stack pop. Handler enabled.
 *
 * Plus two edge-case tests:
 *  4. Empty-string currentRoute is treated defensively as a non-start
 *     destination (avoids silent disabling when Compose hands us an
 *     unexpected empty string).
 *  5. Debug destination (gated by Settings.debugMode) is treated
 *     identically to any other non-start destination — the predicate
 *     does not consult the debugEnabled flag.
 */
class PredictiveBackPresentationLogicTest {

    @Test
    fun `null currentRoute disables the gesture (initial composition guard)`() {
        assertFalse(
            "null route = initial composition; handler must be disabled",
            PredictiveBackPresentation.shouldHandlePredictiveBack(null, "general"),
        )
    }

    @Test
    fun `currentRoute matches startRoute disables the gesture (back exits app, not stack-pop)`() {
        assertFalse(
            "post-onboarding user on General = root; back-press closes app, not stack pops",
            PredictiveBackPresentation.shouldHandlePredictiveBack(
                currentRoute = "general",
                startRoute = "general",
            ),
        )
    }

    @Test
    fun `currentRoute is the onboarding start disables the gesture (cold-start path)`() {
        assertFalse(
            "first-launch user on ONBOARDING = root; back-press closes app",
            PredictiveBackPresentation.shouldHandlePredictiveBack(
                currentRoute = "onboarding",
                startRoute = "onboarding",
            ),
        )
    }

    @Test
    fun `currentRoute is a non-start destination enables the gesture (Models)`() {
        assertTrue(
            "Models != General; back-press from Models should pop to General",
            PredictiveBackPresentation.shouldHandlePredictiveBack(
                currentRoute = "models",
                startRoute = "general",
            ),
        )
    }

    @Test
    fun `currentRoute is a non-start destination enables the gesture (PostProcess)`() {
        assertTrue(
            "PostProcess != General; back-press from PostProcess should pop to General",
            PredictiveBackPresentation.shouldHandlePredictiveBack(
                currentRoute = "post_process",
                startRoute = "general",
            ),
        )
    }

    @Test
    fun `currentRoute is Debug enables the gesture (debugEnabled flag independent)`() {
        // Debug is gated by Settings.debugMode but the predicate does
        // NOT consult that flag — it just checks the route identity.
        // When Debug is gated off, the user can't reach the Debug
        // destination (Option A: DeveloperToolsDisabled placeholder
        // body is rendered but never addressable on the back stack).
        assertTrue(
            "Debug != General; back-press from Debug should pop to About (or whatever previous route)",
            PredictiveBackPresentation.shouldHandlePredictiveBack(
                currentRoute = "debug",
                startRoute = "general",
            ),
        )
    }

    @Test
    fun `empty-string currentRoute is treated as a non-start destination (defensive)`() {
        // Empty route shouldn't happen in practice (NavHost only
        // composes 6 valid routes) but if it does, prefer to keep
        // the handler enabled rather than silently disabling.
        assertTrue(
            "empty string != General; defensive keep-enabled",
            PredictiveBackPresentation.shouldHandlePredictiveBack(
                currentRoute = "",
                startRoute = "general",
            ),
        )
    }
}
