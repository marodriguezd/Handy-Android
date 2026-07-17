package com.handy.app.navigation

/**
 * Sprint 29b — pure JVM-testable logic for the predictive-back gesture
 * opt-in. The Compose-level [androidx.activity.compose.PredictiveBackHandler]
 * in [AppNavigation] root scope consults this helper to decide whether
 * the gesture should fire the back-stack pop on the current destination.
 *
 * Mirrors the [com.handy.app.ui.debug.shouldPopBackStackFromDebug] pattern
 * from Sprint 28b-v11: predicate + helper function in a presentation
 * file, JVM-tested without Robolectric.
 */
object PredictiveBackPresentation {

    /**
     * True iff the predictive back gesture should fire on the current
     * destination. Trivial guard against firing the gesture at the start
     * destination (where there is no back stack to pop) and during
     * initial composition when [currentRoute] is null.
     *
     * @param currentRoute route name from
     *   `navController.currentBackStackEntry?.destination?.route`. May be
     *   null during initial composition before the NavHost has set up.
     *   An empty string is treated defensively as a non-start destination
     *   to avoid silently disabling the handler when Compose hands us an
     *   unexpected value.
     * @param startRoute route name of the graph's start destination
     *   (`if (onboardingCompleted) Screen.General.route else ONBOARDING_ROUTE`).
     * @return `true` when there is somewhere on the back stack to pop;
     *   `false` when the back-press should fall through to system-level
     *   onBackPressed (exit the app on Android 13 and below where the
     *   gesture framework is no-op, or default predictive-back animation
     *   on Android 14+ when the user is on the start destination).
     */
    @JvmStatic
    fun shouldHandlePredictiveBack(
        currentRoute: String?,
        startRoute: String,
    ): Boolean = currentRoute != null && currentRoute != startRoute
}
