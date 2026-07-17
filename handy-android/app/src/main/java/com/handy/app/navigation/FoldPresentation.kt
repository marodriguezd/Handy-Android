package com.handy.app.navigation

/**
 * Sprint 29c — pure JVM-testable logic for the foldable hinge-avoidance
 * padding. The Compose-level integration in MainActivity.kt observes
 * `androidx.window.layout.WindowInfoTracker.windowLayoutInfo(activity)`,
 * but the downstream AppNavigation.kt does NOT need the
 * `androidx.window` types on its classpath to render fold-aware padding.
 *
 * This file defines a parallel data class hierarchy that mirrors the
 * relevant subset of `androidx.window.layout.FoldingFeature` so the
 * Compose-side passes a plain data class instead of an Android
 * framework-bound class. Mirrors the
 * [com.handy.app.ui.debug.DebugPresentation] pattern from Sprint 28b-v11
 * and the [PredictiveBackPresentation] pattern from Sprint 29b.
 */

/**
 * Reduced view of a [androidx.window.layout.FoldingFeature] for the
 * purposes of fold-aware top/bottom bar padding. Pixel coordinates
 * are in window pixels, not dp — `MainActivity.kt` does the conversion
 * to dp at the Compose surface.
 *
 * @property isSeparating `FoldingFeature.isSeparating` — true when the
 *   hinge physically separates the screen into two independent display
 *   regions. False when the hinge is a continuous seam (e.g. large
 *   unfolded foldables like Z Fold4 in FLAT state).
 * @property isHalfOpened `FoldingFeature.state == FoldingFeature.State.HALF_OPENED`.
 *   When true, the device is book-mode and the hinge is a visible
 *   obstruction between the two display regions.
 * @property isHorizontal `FoldingFeature.orientation == HORIZONTAL`. When
 *   false (vertical), the hinge creates a left/right split, which the
 *   TopAppBar/BottomBar layouts cannot dodge by padding alone.
 * @property boundsTop top edge of the hinge in pixels (relative to
 *   the window).
 * @property boundsBottom bottom edge of the hinge in pixels (relative
 *   to the window).
 */
data class FoldingFeatureInfo(
    val isSeparating: Boolean,
    val isHalfOpened: Boolean,
    val isHorizontal: Boolean,
    val boundsTop: Int,
    val boundsBottom: Int,
)

/**
 * Pure function surface for fold-aware padding computation.
 *
 * The implementation is intentionally a thin object so JVM tests can
 * call it without instantiating Android `androidx.window` types.
 */
object FoldPresentation {

    /**
     * Compute the top + bottom pixel padding needed to dodge the hinge
     * on a foldable device in HALF_OPENED book mode (or in any state
     * where the hinge physically separates the display into two regions).
     *
     * Returns `Pair(0, 0)` when:
     *  - feature is `null` (no fold detected, common on phones/tablets)
     *  - `!isSeparating && !isHalfOpened` (continuous-screen foldable in
     *    FLAT state, e.g. Z Fold4 fully unfolded)
     *  - `!isHorizontal` (vertical split: top/bottom bars cannot dodge
     *    by padding — the user could not read on a left/right hinge
     *    layout regardless)
     *  - hinge straddles the screen vertical midline (the top half is the
     *    main content area and the bottom half is the bottom-nav area —
     *    no padding adjustment cleanly available; both bars may partially
     *    overlap the hinge so we let the layout decide)
     *
     * Otherwise:
     *  - If `boundsBottom <= screenHeightPx / 2` (hinge in the **upper**
     *    half of the screen) → top padding = `boundsBottom`, bottom = 0.
     *    The TopAppBar gets pushed below the hinge; the BottomBar stays
     *    aligned to the screen bottom.
     *  - If `boundsTop >= screenHeightPx / 2` (hinge in the **lower**
     *    half of the screen) → top = 0, bottom = `screenHeightPx - boundsTop`.
     *    The TopAppBar stays at the screen top; the BottomBar gets pushed
     *    above the hinge.
     *
     * @param feature foldable hinge state from WindowInfoTracker, or null
     *   if no DisplayFeature is currently active.
     * @param screenHeightPx window height in pixels (used to bisect the
     *   screen into upper/lower halves for the dodge decision).
     * @return `Pair(topPaddingPx, bottomPaddingPx)` ready to convert to
     *   dp via Compose `LocalDensity` and apply as `Modifier.padding(top, bottom)`.
     */
    @JvmStatic
    fun computeHingePaddingPx(
        feature: FoldingFeatureInfo?,
        screenHeightPx: Int,
    ): Pair<Int, Int> {
        if (feature == null) return 0 to 0
        if (!feature.isSeparating && !feature.isHalfOpened) return 0 to 0
        if (!feature.isHorizontal) return 0 to 0

        val midY = screenHeightPx / 2
        return when {
            // Hinge in upper half — pad TopAppBar to clear the bottom of the hinge.
            feature.boundsBottom <= midY -> feature.boundsBottom to 0
            // Hinge in lower half — pad BottomBar to clear the top of the hinge.
            feature.boundsTop >= midY -> 0 to (screenHeightPx - feature.boundsTop)
            // Hinge straddles midline — let the layout decide; no padding adjustment.
            else -> 0 to 0
        }
    }
}
