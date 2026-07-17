package com.handy.app.ui.debug

import androidx.annotation.StringRes
import com.handy.app.R

/**
 * Sprint 28b-v11 — pure-presentation logic for the developer-facing debug gate
 * wiring. JVM-testable so we can assert the contract end-to-end without
 * Robolectric.
 *
 * Three concerns stay here (in MVP+):
 *   - Visibility predicate — drives the developer's actual Debug screen vs the
 *     DeveloperToolsDisabled placeholder.
 *   - PopBackStack guard — drives the auto-redirect to the General destination
 *     when the user is currently on the Debug route and flips the gate off.
 *   - Feedback message picker — picks the right string-resource ID for the
 *     Snackbar shown after a toggle flip.
 */

/**
 * Whether the developer-facing Debug panel should render (vs the
 * [DeveloperToolsDisabled] placeholder).
 *
 * Trivial wrapper kept as a named function for test surface clarity — the
 * pure-function form makes it grep-able from Sprint 29 refactors that might
 * decide to add a richer rule (e.g. "show disabled body if debugEnabled=false
 * OR onboarding hasn't been completed").
 */
internal fun isDeveloperToolsVisible(debugEnabled: Boolean): Boolean = debugEnabled

/**
 * True iff the user was on the Debug destination AND the gate flipped from
 * ENABLE to DISABLE in this composition. The caller is then responsible for
 * `navController.navigate(Screen.General.route) { popUpTo(...) }`. Keeping it
 * pure means the LaunchedEffect wrapper doesn't need its own test surface.
 *
 * Safe transitions (DEBUG→DEBUG, other routes→other routes) return false.
 *
 * @param currentRoute route name from `navController.currentBackStackEntry`'s
 *   destination, may be null during initial composition.
 * @param debugEnabledNow current value of the gate (post-flip, if any).
 * @param debugEnabledPrev previous value of the gate (pre-flip, if any).
 */
internal fun shouldPopBackStackFromDebug(
    currentRoute: String?,
    debugEnabledNow: Boolean,
    debugEnabledPrev: Boolean,
): Boolean =
    currentRoute == "debug" && debugEnabledPrev && !debugEnabledNow

/**
 * Resolves a `@StringRes` id for the message rendered in the
 * post-flip Snackbar. Pure — the Compose layer only reads this at snackbar
 * emission time.
 *
 * Maps:
 *   - `true` (gate flipped ON) → `R.string.debug_toggle_enabled_feedback`
 *   - `false` (gate flipped OFF) → `R.string.debug_toggle_disabled_feedback`
 */
@StringRes
internal fun getSnackbarMessageForFlip(debugEnabledAfter: Boolean): Int =
    if (debugEnabledAfter) {
        R.string.debug_toggle_enabled_feedback
    } else {
        R.string.debug_toggle_disabled_feedback
    }

/** Stable name of the Debug destination — mirrors `Screen.Debug.route` to keep
 *  DebugPresentation.kt free of `AppNavigation` cross-package coupling. */
internal const val DEBUG_ROUTE: String = "debug"
