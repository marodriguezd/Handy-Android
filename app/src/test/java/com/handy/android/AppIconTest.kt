package com.handy.android

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

/** Validates the brand launcher icon: manifest wiring, adaptive-icon layers and safe-zone geometry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppIconTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val androidNs = "http://schemas.android.com/apk/res/android"

    @Test
    fun manifestPointsToAdaptiveLauncherIcons() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals("mipmap", context.resources.getResourceTypeName(info.icon))
        assertEquals("ic_launcher", context.resources.getResourceEntryName(info.icon))

        // ApplicationInfo.roundIcon is absent from the compile-time android.jar stub (SDK 35),
        // so verify the manifest wiring directly from the authored source manifest.
        // Gradle runs unit tests with the module directory as the working directory.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("missing android:icon", manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue("missing android:roundIcon", manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))

        // Both resources must resolve.
        assertTrue(
            "mipmap/ic_launcher_round missing",
            context.resources.getIdentifier("ic_launcher_round", "mipmap", context.packageName) != 0,
        )
    }

    @Test
    fun adaptiveIconReferencesBackgroundForegroundAndMonochrome() {
        val layers = adaptiveLayers(R.mipmap.ic_launcher)
        assertEquals("drawable/ic_launcher_background", layers["background"])
        assertEquals("drawable/ic_launcher_foreground", layers["foreground"])
        assertEquals("drawable/ic_launcher_foreground", layers["monochrome"])
    }

    @Test
    fun roundIconReferencesTheSameLayers() {
        val layers = adaptiveLayers(R.mipmap.ic_launcher_round)
        assertEquals("drawable/ic_launcher_background", layers["background"])
        assertEquals("drawable/ic_launcher_foreground", layers["foreground"])
        assertEquals("drawable/ic_launcher_foreground", layers["monochrome"])
    }

    @Test
    fun launcherIconInflatesWithAllLayers() {
        val drawable = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue("expected AdaptiveIconDrawable", drawable is AdaptiveIconDrawable)
        val adaptive = drawable as AdaptiveIconDrawable
        assertNotNull(adaptive.background)
        assertNotNull(adaptive.foreground)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            assertNotNull(adaptive.monochrome)
        }
    }

    @Test
    fun brandGlyphsShareTheSameMicrophonePathAndLauncherTransform() {
        // ic_mic, ic_stat_handy and the launcher foreground must keep the exact same mic path
        // so the brand glyph never drifts between the UI icon, the status-bar/QS glyph and the
        // launcher. Only fill colour and the adaptive group transform may differ.
        val micPath = vectorPathData(R.drawable.ic_mic)
        assertEquals("ic_stat_handy must reuse ic_mic's path", micPath, vectorPathData(R.drawable.ic_stat_handy))
        assertEquals("launcher foreground must reuse ic_mic's path", micPath, vectorPathData(R.drawable.ic_launcher_foreground))

        // The launcher glyph must remain the scaled+centred version inside the 108dp safe zone.
        // Pinned exactly here (in addition to the geometric safe-zone check) so a redesign that
        // changes the scale/offset is a conscious, reviewed change.
        val transform = readForegroundTransform()
        assertEquals(2.1f, transform.scaleX, 0.001f)
        assertEquals(2.1f, transform.scaleY, 0.001f)
        assertEquals(28.8f, transform.translateX, 0.001f)
        assertEquals(28.8f, transform.translateY, 0.001f)
    }

    @Test
    fun foregroundGlyphStaysWithinAdaptiveSafeZone() {
        val transform = readForegroundTransform()

        // Material mic glyph bounds in the 24-unit source viewport: x in [5,19], y in [2,21].
        val left = transform.translateX + 5f * transform.scaleX
        val right = transform.translateX + 19f * transform.scaleX
        val top = transform.translateY + 2f * transform.scaleY
        val bottom = transform.translateY + 21f * transform.scaleY
        val centerX = (left + right) / 2f
        val centerY = (top + bottom) / 2f

        // Adaptive icon spec: 108dp canvas, content must stay inside the 66dp safe circle
        // (radius 33) centred on (54, 54).
        val corners = listOf(left to top, right to top, left to bottom, right to bottom)
        corners.forEach { (x, y) ->
            val distance = kotlin.math.sqrt((x - 54f) * (x - 54f) + (y - 54f) * (y - 54f))
            assertTrue("corner ($x,$y) is $distance dp from centre, outside the 33dp safe radius", distance <= 33f)
        }

        // Glyph is roughly centred and reasonably sized (launchers clip anything too small).
        assertTrue("glyph centre x=$centerX not centred", kotlin.math.abs(centerX - 54f) <= 2f)
        assertTrue("glyph centre y=$centerY not centred", kotlin.math.abs(centerY - 54f) <= 2f)
        assertTrue("glyph too small", (right - left) >= 24f && (bottom - top) >= 24f)
    }

    private fun adaptiveLayers(resId: Int): Map<String, String> {
        val parser = context.resources.getXml(resId)
        val layers = mutableMapOf<String, String>()
        var firstTag: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                if (firstTag == null) firstTag = parser.name
                if (parser.name in setOf("background", "foreground", "monochrome")) {
                    val drawableId = parser.getAttributeResourceValue(androidNs, "drawable", 0)
                    assertTrue("layer drawable missing (id $drawableId)", drawableId != 0)
                    layers[parser.name] =
                        "${context.resources.getResourceTypeName(drawableId)}/${context.resources.getResourceEntryName(drawableId)}"
                }
            }
            event = parser.next()
        }
        assertEquals("expected adaptive-icon root", "adaptive-icon", firstTag)
        return layers
    }

    private fun vectorPathData(resId: Int): String {
        val parser = context.resources.getXml(resId)
        var pathData: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "path") {
                // Exactly one <path> per vector: a decorative second path would otherwise let
                // the parity check pass while the primary mic glyph drifts.
                assertNull("expected a single <path> in resId=$resId", pathData)
                pathData = parser.getAttributeValue(androidNs, "pathData")
                assertNotNull("path element must define pathData (resId=$resId)", pathData)
            }
            event = parser.next()
        }
        assertNotNull("vector must contain a <path> (resId=$resId)", pathData)
        return normalizePath(pathData!!)
    }

    private fun normalizePath(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()

    private fun readForegroundTransform(): ForegroundTransform {
        val parser = context.resources.getXml(R.drawable.ic_launcher_foreground)
        var transform = ForegroundTransform(1f, 1f, 0f, 0f)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "group") {
                transform = ForegroundTransform(
                    scaleX = parser.getAttributeValue(androidNs, "scaleX")?.toFloat() ?: 1f,
                    scaleY = parser.getAttributeValue(androidNs, "scaleY")?.toFloat() ?: 1f,
                    translateX = parser.getAttributeValue(androidNs, "translateX")?.toFloat() ?: 0f,
                    translateY = parser.getAttributeValue(androidNs, "translateY")?.toFloat() ?: 0f,
                )
            }
            event = parser.next()
        }
        return transform
    }

    private data class ForegroundTransform(
        val scaleX: Float,
        val scaleY: Float,
        val translateX: Float,
        val translateY: Float,
    )
}
