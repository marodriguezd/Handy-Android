package com.handy.android

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F8 regression: `AutoTypeAccessibilityService.performImeEnter` must use the platform
 * [AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER] constant (API 31+) instead of
 * the raw magic number `0x00400000`.
 *
 * On a real device, `ACTION_IME_ENTER.id` is `0x00400000` (defined explicitly in AOSP
 * `accessibility_action_ids.xml`). Robolectric **remaps** framework resource ids to its own
 * table (here it resolves to a different numeric id), so these tests only assert the
 * environment-independent contract:
 *  - the constant exists at API 31+ (the level the service guards with `VERSION_CODES.S`);
 *  - its id is valid and distinct from other standard actions, so the service performs the
 *    IME Enter action and not some unrelated accessibility action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class AccessibilityActionTest {

    @Test
    fun `ACTION_IME_ENTER constant exists on API 31+ with a valid id`() {
        val imeEnter = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
        assertNotNull("ACTION_IME_ENTER must be non-null on API 31+", imeEnter)
        assertTrue("ACTION_IME_ENTER.id must be a positive action id", imeEnter.id > 0)
    }

    @Test
    fun `ACTION_IME_ENTER id differs from standard actions`() {
        val imeEnter = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
        assertNotNull(imeEnter)
        assertNotEquals("IME Enter must not be confused with a click", AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id, imeEnter.id)
        assertNotEquals("IME Enter must not be confused with focus", AccessibilityNodeInfo.AccessibilityAction.ACTION_FOCUS.id, imeEnter.id)
    }
}
