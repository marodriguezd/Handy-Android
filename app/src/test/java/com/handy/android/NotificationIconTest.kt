package com.handy.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for the brand-notification-icon audit item: notification small icons must use the
 * brand `ic_stat_handy` drawable (white alpha-mask mic) instead of generic `android.R.drawable.*`
 * icons, and the Quick Settings tile must declare the same brand glyph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationIconTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun brandSmallIconResolvesAndInflatesAsVector() {
        val iconId = context.resources.getIdentifier("ic_stat_handy", "drawable", context.packageName)
        assertTrue("ic_stat_handy must exist in drawable", iconId != 0)
        val drawable = context.getDrawable(iconId)
        assertNotNull("ic_stat_handy must inflate", drawable)
        // White alpha-mask glyph: single white path, no other colours.
        val source = File("src/main/res/drawable/ic_stat_handy.xml").readText()
        assertTrue("vector must declare the white tint", source.contains("#FFFFFFFF"))
    }

    @Test
    fun floatingButtonServiceUsesBrandIconNotSystemIcon() {
        val source = File("src/main/java/com/handy/android/FloatingButtonService.kt").readText()
        assertTrue(
            "FloatingButtonService must use R.drawable.ic_stat_handy",
            source.contains("setSmallIcon(R.drawable.ic_stat_handy)"),
        )
        assertTrue("no android.R.drawable small icon allowed", !source.contains("android.R.drawable"))
    }

    @Test
    fun liveSubtitleServiceUsesBrandIconNotSystemIcon() {
        val source = File("src/main/java/com/handy/android/LiveSubtitleService.kt").readText()
        assertTrue(
            "LiveSubtitleService must use R.drawable.ic_stat_handy",
            source.contains("setSmallIcon(R.drawable.ic_stat_handy)"),
        )
        assertTrue("no android.R.drawable small icon allowed", !source.contains("android.R.drawable"))
    }

    @Test
    fun quickSettingsTileDeclaresBrandIcon() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val tile = manifest.substringAfter("HandyTileService", "")
        assertTrue("tile service must declare the brand drawable", tile.contains("android:icon=\"@drawable/ic_stat_handy\""))
    }
}
