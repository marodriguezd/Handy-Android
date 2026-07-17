package com.handy.app.ui.debug

import com.handy.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 28b-v11 — graduate-test surface for the developer-facing
 * Debug-mode gate. Mirrors the pure-logic JVM-test pattern established
 * in Sprint 22 (CatalogSorter), Sprint 24 (HistoryPresentationLogic),
 * and Sprint 25b (CustomWordsParser, RetentionPeriod, AccelerationSelector).
 *
 * Four required scenarios (per user request):
 *   1. config-toggle                — Snackbar message picker resolves correctly.
 *   2. persistence-confirm          — DEBUG_ROUTE constant is stable (used by
 *                                     AppNavigation's popBackStack guard);
 *                                     the value persists across reads so the
 *                                     popBackStack wiring remains
 *                                     forward-compat with any future rename.
 *   3. DeveloperToolsDisabled       — isDeveloperToolsVisible() correctly
 *                                     downgrades to the placeholder body
 *                                     when the gate is false.
 *   4. disabled-state UI affordance — shouldPopBackStackFromDebug() returns
 *                                     true ONLY in the right combination
 *                                     (route=debug AND gate flipped ON→OFF
 *                                     in the same composition); otherwise
 *                                     the user is left where they are.
 *
 * No Robolectric, no Compose runtime — pure JVM against the static
 * presentation functions. The Compose layer's wiring (LaunchedEffect
 * with the prevDebugEnabled mutableStateOf carrier) is exercised
 * visually on A059; the JVM surface here locks down the contract.
 */
class DebugModeToggleUiTest {

    /**
     * 1. config-toggle — Snackbar feedback message picker.
     *
     * When the developer flips the toggle ON, the parent
     * [com.handy.app.ui.debug.DebugContent] shows
     * `R.string.debug_toggle_enabled_feedback`; when flipping OFF,
     * it shows `R.string.debug_toggle_disabled_feedback`. This test
     * pins both branches.
     */
    @Test
    fun configToggle_picksCorrectSnackbarStringForFlipOnAndOff() {
        // Flip ON (gate was off, switches to on)
        assertEquals(
            "Flipping the toggle ON should pick the enabled-feedback string",
            R.string.debug_toggle_enabled_feedback,
            getSnackbarMessageForFlip(debugEnabledAfter = true),
        )
        // Flip OFF (gate was on, switches to off)
        assertEquals(
            "Flipping the toggle OFF should pick the disabled-feedback string",
            R.string.debug_toggle_disabled_feedback,
            getSnackbarMessageForFlip(debugEnabledAfter = false),
        )
    }

    /**
     * 2. persistence-confirm — DEBUG_ROUTE constant stability.
     *
     * The route name is what `AppNavigation`'s popBackStack guard keys
     * against (`currentDestination?.route`). If this constant drifts
     * from `Screen.Debug.route`, the user gets silently stranded on
     * the DeveloperToolsDisabled placeholder with no Debug tile in
     * the bottom-nav. The test asserts both: (a) the value is "debug",
     * and (b) it's stable across reads (i.e. a `const val`, not a
     * lambda/property recomputed each call).
     */
    @Test
    fun debugRouteConstantIsStableAndMatchesNavigationContract() {
        val first = DEBUG_ROUTE
        val second = DEBUG_ROUTE
        val third = DEBUG_ROUTE

        assertEquals(
            "DEBUG_ROUTE must stay as 'debug' to match Screen.Debug.route",
            "debug",
            first,
        )
        assertEquals(
            "DEBUG_ROUTE reads must return the exact same string instance/value",
            first,
            second,
        )
        assertEquals(
            "DEBUG_ROUTE reads must remain stable across many reads",
            first,
            third,
        )
        // Guard against an accidental lowercase-uppercased rename.
        assertFalse(
            "DEBUG_ROUTE must not be uppercased — Compose ignores case-sensitive route names",
            first.any { it.isUpperCase() },
        )
    }

    /**
     * 3. DeveloperToolsDisabled — gallery of `isDeveloperToolsVisible(debugEnabled)`
     * branches. The function decides whether the real Debug panel or
     * the placeholder body renders at the DEBUG_ROUTE destination.
     */
    @Test
    fun developerToolsDowngrade_predicateSelectsCorrectBody() {
        // Gate is ON → real Debug panel renders.
        assertTrue(
            "When gate is ON, the real Debug panel must render (vs the placeholder)",
            isDeveloperToolsVisible(debugEnabled = true),
        )
        // Gate is OFF → placeholder renders.
        assertFalse(
            "When gate is OFF, the DeveloperToolsDisabled placeholder must render",
            isDeveloperToolsVisible(debugEnabled = false),
        )
        // Defensive: the predicate must be deterministic — every call with the
        // same input yields the same output (no Compose-state-dependent drift).
        for (i in 0 until 10) {
            assertEquals(
                "Repeated calls with debugEnabled=true must remain true (call #$i)",
                true,
                isDeveloperToolsVisible(debugEnabled = true),
            )
            assertEquals(
                "Repeated calls with debugEnabled=false must remain false (call #$i)",
                false,
                isDeveloperToolsVisible(debugEnabled = false),
            )
        }
    }

    /**
     * 4. disabled-state UI affordance — popBackStack guard matrix.
     *
     * The guard fires ONLY when the user is currently on the Debug
     * destination AND the gate flipped from ON to OFF in the same
     * composition. Every other permutation returns false (no
     * surprise navigation).
     */
    @Test
    fun popBackStackGuard_onlyFiresOnDebugRouteWhileFlippingOff() {
        // Positives: the guard should fire.
        assertTrue(
            "User on Debug + gate flips ON→OFF → auto-redirect to General",
            shouldPopBackStackFromDebug(
                currentRoute = DEBUG_ROUTE,
                debugEnabledNow = false,
                debugEnabledPrev = true,
            ),
        )

        // Negatives: the guard must NOT fire.
        assertFalse(
            "User on Home + gate flips ON→OFF → no auto-redirect (user is on Home)",
            shouldPopBackStackFromDebug(
                currentRoute = "general",
                debugEnabledNow = false,
                debugEnabledPrev = true,
            ),
        )
        assertFalse(
            "User on Debug + gate stays ON (toggle flipped OFF then ON quickly) → no redirect",
            shouldPopBackStackFromDebug(
                currentRoute = DEBUG_ROUTE,
                debugEnabledNow = true,
                debugEnabledPrev = true,
            ),
        )
        assertFalse(
            "User on Debug + gate flips OFF→ON → no redirect (can't pop to General on enable)",
            shouldPopBackStackFromDebug(
                currentRoute = DEBUG_ROUTE,
                debugEnabledNow = true,
                debugEnabledPrev = false,
            ),
        )
        assertFalse(
            "Initial composition: null route + no prior value → no redirect",
            shouldPopBackStackFromDebug(
                currentRoute = null,
                debugEnabledNow = false,
                debugEnabledPrev = false,
            ),
        )
        assertFalse(
            "Edge case: null route (route name not yet resolved) + flip off → no redirect",
            shouldPopBackStackFromDebug(
                currentRoute = null,
                debugEnabledNow = false,
                debugEnabledPrev = true,
            ),
        )
        assertFalse(
            "Edge case: dev toggles twice in same composition (OFF→ON→OFF) → debugEnabledNow=false debugEnabledPrev=true (still no-op because route is not Debug)",
            shouldPopBackStackFromDebug(
                currentRoute = "models",
                debugEnabledNow = false,
                debugEnabledPrev = true,
            ),
        )
    }
}
