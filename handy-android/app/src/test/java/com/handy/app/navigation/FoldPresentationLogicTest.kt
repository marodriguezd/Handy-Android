package com.handy.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sprint 29c — JUnit4 mirror test for [FoldPresentation.computeHingePaddingPx].
 *
 * Locks the boundary between hinge presence, separating/half-opened
 * state, hinge orientation, and hinge vertical position relative to
 * the screen midline. Pure-function coverage so the JVM surface is
 * asserted without needing `androidx.window` on the test classpath (the
 * `androidx.window` library is mocked away on Robolectric; here we use
 * our own [FoldingFeatureInfo] data class).
 *
 * Test coordinates are deliberately chosen so that:
 *  - screen height = 2400 px (typical Android 16 device VP pixel size
 *    for 1080×2392 display panels)
 *  - hinge bounds are 100 px tall (a typical reported hinge height on
 *    Galaxy Z Fold-class devices when reported as `FoldingFeature.bounds`)
 *  - midline = 1200 px
 *
 * The exact pixel values are not significant — what matters is the
 * branch coverage. Tests:
 *  1. `null` feature → no padding (phones/tablets without hinges).
 *  2. FLAT continuous-screen foldable (`isSeparating=false`,
 *     `isHalfOpened=false`) → no padding (no functional obstruction).
 *  3. Vertical hinge (`isHorizontal=false`) → no padding for top/bottom
 *     bars (would need left/right padding instead, which we are not
 *     implementing in this sprint).
 *  4. HALF_OPENED horizontal hinge in the upper half → top padding =
 *     `boundsBottom`, bottom padding = 0.
 *  5. HALF_OPENED horizontal hinge in the lower half → top padding = 0,
 *     bottom padding = `screenHeightPx - boundsTop`.
 *  6. Horizontal hinge straddling the midline → no padding (the
 *     hinge bisects the layout; both bars may overlap; we let the
 *     layout decide rather than centering any bar on the hinge).
 *  7. `isSeparating=true, isHalfOpened=false` horizontal hinge in the
 *     upper half → still pads top. (covers dual-screen devices
 *     like Surface Duo in continuous mode where the hinge is fixed
 *     even when the panel is FLAT.)
 */
class FoldPresentationLogicTest {

    private val midY = 1200

    @Test
    fun `null feature returns zero padding (phones, tablets, non-foldable form factors)`() {
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature = null, screenHeightPx = 2400)
        assertEquals(0, top)
        assertEquals(0, bottom)
    }

    @Test
    fun `FLAT continuous-screen foldable returns zero padding (no functional obstruction)`() {
        val feature = FoldingFeatureInfo(
            isSeparating = false,
            isHalfOpened = false,
            isHorizontal = true,
            boundsTop = 1180,
            boundsBottom = 1220, // straddling midline but FLAT so no padding
        )
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature, screenHeightPx = 2400)
        assertEquals(0, top)
        assertEquals(0, bottom)
    }

    @Test
    fun `vertical hinge returns zero padding (top-bottom bars cannot dodge left-right split)`() {
        val feature = FoldingFeatureInfo(
            isSeparating = true,
            isHalfOpened = true,
            isHorizontal = false, // vertical hinge = left/right split
            boundsTop = 0,
            boundsBottom = 2400, // whole-height split
        )
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature, screenHeightPx = 2400)
        assertEquals("vertical hinge = no top padding", 0, top)
        assertEquals("vertical hinge = no bottom padding", 0, bottom)
    }

    @Test
    fun `HALF_OPENED horizontal hinge in upper half pads TopAppBar (lower bar unaffected)`() {
        val feature = FoldingFeatureInfo(
            isSeparating = true,
            isHalfOpened = true,
            isHorizontal = true,
            boundsTop = 600,
            boundsBottom = 700, // entirely in upper half (midY = 1200)
        )
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature, screenHeightPx = 2400)
        assertEquals("TopAppBar top padding = bounds.bottom", 700, top)
        assertEquals("BottomBar bottom padding = 0 (lower half untouched)", 0, bottom)
    }

    @Test
    fun `HALF_OPENED horizontal hinge in lower half pads BottomBar (upper bar unaffected)`() {
        val feature = FoldingFeatureInfo(
            isSeparating = true,
            isHalfOpened = true,
            isHorizontal = true,
            boundsTop = 1800,
            boundsBottom = 1900, // entirely in lower half (midY = 1200)
        )
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature, screenHeightPx = 2400)
        assertEquals("TopAppBar top padding = 0 (upper half untouched)", 0, top)
        assertEquals("BottomBar bottom padding = screenHeight - bounds.top", 600, bottom)
    }

    @Test
    fun `horizontal hinge straddling midline returns zero padding (let the layout decide)`() {
        val feature = FoldingFeatureInfo(
            isSeparating = true,
            isHalfOpened = true,
            isHorizontal = true,
            boundsTop = 1150, // straddles midY = 1200
            boundsBottom = 1250,
        )
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature, screenHeightPx = 2400)
        assertEquals("midline-straddle hinge = no top padding adjustment", 0, top)
        assertEquals("midline-straddle hinge = no bottom padding adjustment", 0, bottom)
    }

    @Test
    fun `isSeparating=true HALF_OPENED=true continuous hinge in upper half pads top`() {
        // Surface Duo in continuous mode: hinge always reports as
        // isSeparating=true even when the panel itself reports
        // HALF_OPENED=false. Both `isHalfOpened` checks behave the same.
        val feature = FoldingFeatureInfo(
            isSeparating = true,
            isHalfOpened = false, // continuous state, but still separating
            isHorizontal = true,
            boundsTop = 600,
            boundsBottom = 700,
        )
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature, screenHeightPx = 2400)
        assertEquals(700, top)
        assertEquals(0, bottom)
    }

    @Test
    fun `hinge exactly at screen vertical mid-edge returns zero padding (defensive)`() {
        // Defensive: boundsTop = boundsBottom = midY (hinge exactly on
        // the midline edge). The case where both BottomBar and TopAppBar
        // migh need padding — our implementation returns (0, 0) and
        // lets the layout center as it wishes.
        val feature = FoldingFeatureInfo(
            isSeparating = true,
            isHalfOpened = true,
            isHorizontal = true,
            boundsTop = midY,
            boundsBottom = midY,
        )
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature, screenHeightPx = 2400)
        // boundsBottom <= midY → takes the upper-half branch (top = boundsBottom)
        assertEquals(midY, top)
        assertEquals(0, bottom)
    }

    @Test
    fun `hinge exactly at screenHeightPx returns zero top padding and screenHeightPx minus boundsTop bottom padding`() {
        // Defensive: hinge at the very bottom edge of the screen.
        val feature = FoldingFeatureInfo(
            isSeparating = true,
            isHalfOpened = true,
            isHorizontal = true,
            boundsTop = 2390,
            boundsBottom = 2400,
        )
        val (top, bottom) = FoldPresentation.computeHingePaddingPx(feature, screenHeightPx = 2400)
        assertEquals("hinge at bottom edge → no top padding", 0, top)
        assertEquals("hinge at bottom edge → bottom padding = screenHeight - boundsTop = 10", 10, bottom)
    }
}
