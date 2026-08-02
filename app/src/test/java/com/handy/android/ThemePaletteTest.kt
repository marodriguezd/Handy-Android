package com.handy.android

import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the brand palette against drift between its two homes:
 *  - Compose theme: `ui/theme/Color.kt` (`md_theme_light_*` constants used by [HandyTheme])
 *  - Views/overlay/notification resources: `res/values/colors.xml` (`handy_*`)
 * Both must keep identical values, since services (floating overlay, subtitle bar) and the
 * IME live outside Compose and resolve colours from the resource table.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePaletteTest {

    private val colorsXml: String
        get() = File("src/main/res/values/colors.xml").readText()

    private val colorKt: String
        get() = File("src/main/java/com/handy/android/ui/theme/Color.kt").readText()

    @Test
    fun viewPaletteMatchesComposeLightPalette() {
        // handy_* (colors.xml) must equal the corresponding md_theme_light_* (Color.kt).
        val pairs = mapOf(
            "handy_primary" to "md_theme_light_primary",
            "handy_on_primary" to "md_theme_light_onPrimary",
            "handy_primary_container" to "md_theme_light_primaryContainer",
            "handy_tertiary" to "md_theme_light_tertiary",
            "handy_surface" to "md_theme_light_surface",
            "handy_on_surface" to "md_theme_light_onSurface",
            "handy_on_surface_variant" to "md_theme_light_onSurfaceVariant",
            "handy_outline" to "md_theme_light_outline",
        )
        pairs.forEach { (xmlName, ktName) ->
            val xmlValue = colorValueFromXml(xmlName)
            val ktValue = colorValueFromKt(ktName)
            assertEquals("$xmlName (colors.xml) must match $ktName (Color.kt)", ktValue, xmlValue)
        }

        // Dark tokens used by the window/splash theme must mirror the Compose dark palette too.
        val darkPairs = mapOf(
            "handy_primary_dark" to "md_theme_dark_primary",
            "handy_primary_dark_container" to "md_theme_dark_primaryContainer",
            "handy_surface_dark" to "md_theme_dark_surface",
        )
        darkPairs.forEach { (xmlName, ktName) ->
            assertEquals("$xmlName (colors.xml) must match $ktName (Color.kt)", colorValueFromKt(ktName), colorValueFromXml(xmlName))
        }
    }

    @Test
    fun windowThemeReferencesPaletteResourcesNotRawHex() {
        val styles = File("src/main/res/values/styles.xml").readText()
        val night = File("src/main/res/values-night/styles.xml").readText()
        // No raw hex literals in the window theme: everything must resolve from @color/handy_*.
        val hexInStyles = Regex("#[0-9A-Fa-f]{6,8}").findAll(styles + night).count()
        assertEquals("window themes must not hardcode palette hex values", 0, hexInStyles)
        assertTrue("light theme must reference @color/handy_surface", styles.contains("@color/handy_surface"))
        assertTrue("dark theme must reference @color/handy_surface_dark", night.contains("@color/handy_surface_dark"))
    }

    @Test
    fun expandedMd3RolesMatchCanonicalBaseline() {
        // Canonical Material 3 baseline for the brand seed #6750A4 (Material Theme Builder).
        // These match androidx ColorLightTokens/ColorDarkTokens (Neutral94/Error40/Primary80...).
        val canonical = mapOf(
            "md_theme_light_error" to "B3261E",
            "md_theme_light_onError" to "FFFFFF",
            "md_theme_light_errorContainer" to "F9DEDC",
            "md_theme_light_onErrorContainer" to "410E0B",
            "md_theme_light_inverseSurface" to "322F35",
            "md_theme_light_inverseOnSurface" to "F5EFF7",
            "md_theme_light_inversePrimary" to "D0BCFF",
            "md_theme_light_outlineVariant" to "CAC4D0",
            "md_theme_light_surfaceDim" to "DED8E1",
            "md_theme_light_surfaceBright" to "FEF7FF",
            "md_theme_light_surfaceContainerLowest" to "FFFFFF",
            "md_theme_light_surfaceContainerLow" to "F7F2FA",
            "md_theme_light_surfaceContainer" to "F3EDF7",
            "md_theme_light_surfaceContainerHigh" to "EDE7F0",
            "md_theme_light_surfaceContainerHighest" to "E7E0EB",
            "md_theme_dark_error" to "F2B8B5",
            "md_theme_dark_onError" to "601410",
            "md_theme_dark_errorContainer" to "8C1D18",
            "md_theme_dark_onErrorContainer" to "F9DEDC",
            "md_theme_dark_inverseSurface" to "E6E0E9",
            "md_theme_dark_inverseOnSurface" to "322F35",
            "md_theme_dark_inversePrimary" to "6750A4",
            "md_theme_dark_outlineVariant" to "49454F",
            "md_theme_dark_surfaceDim" to "141218",
            "md_theme_dark_surfaceBright" to "3B383E",
            "md_theme_dark_surfaceContainerLowest" to "0F0D13",
            "md_theme_dark_surfaceContainerLow" to "1D1B20",
            "md_theme_dark_surfaceContainer" to "211F26",
            "md_theme_dark_surfaceContainerHigh" to "2B2930",
            "md_theme_dark_surfaceContainerHighest" to "36343B",
        )
        canonical.forEach { (constant, expectedRgb) ->
            assertEquals("$constant must match the canonical baseline", expectedRgb, colorValueFromKt(constant))
        }
    }

    @Test
    fun surfaceContainerFamilyIsTonallyOrdered() {
        // MD3 spec: surfaceContainerLowest < Low < Container < High < Highest by tone.
        // In light mode higher container roles are darker; assert the luminance ordering.
        val light = listOf(
            luminanceOf(colorValueFromKt("md_theme_light_surfaceContainerLowest")),
            luminanceOf(colorValueFromKt("md_theme_light_surfaceContainerLow")),
            luminanceOf(colorValueFromKt("md_theme_light_surfaceContainer")),
            luminanceOf(colorValueFromKt("md_theme_light_surfaceContainerHigh")),
            luminanceOf(colorValueFromKt("md_theme_light_surfaceContainerHighest")),
        )
        assertTrue("light surfaceContainer must be tonally ordered", light == light.sortedDescending())

        // Dark mode inverts: surfaceContainerLowest is darkest, Highest is brightest.
        val dark = listOf(
            luminanceOf(colorValueFromKt("md_theme_dark_surfaceContainerLowest")),
            luminanceOf(colorValueFromKt("md_theme_dark_surfaceContainerLow")),
            luminanceOf(colorValueFromKt("md_theme_dark_surfaceContainer")),
            luminanceOf(colorValueFromKt("md_theme_dark_surfaceContainerHigh")),
            luminanceOf(colorValueFromKt("md_theme_dark_surfaceContainerHighest")),
        )
        assertTrue("dark surfaceContainer must be tonally ordered", dark == dark.sorted())
    }

    @Test
    fun errorRolesProvideSufficientContrast() {
        // WCAG AA for normal text: >= 4.5:1 between error/onError and container pairs.
        // Values derived from Color.kt (same source of truth as the canonical baseline test)
        // so a palette regeneration fails both tests consistently instead of silently.
        assertTrue("light error/onError contrast", contrastRatio(colorValueFromKt("md_theme_light_error"), colorValueFromKt("md_theme_light_onError")) >= 4.5)
        assertTrue("light errorContainer/onErrorContainer contrast", contrastRatio(colorValueFromKt("md_theme_light_errorContainer"), colorValueFromKt("md_theme_light_onErrorContainer")) >= 4.5)
        assertTrue("dark error/onError contrast", contrastRatio(colorValueFromKt("md_theme_dark_error"), colorValueFromKt("md_theme_dark_onError")) >= 4.5)
        assertTrue("dark errorContainer/onErrorContainer contrast", contrastRatio(colorValueFromKt("md_theme_dark_errorContainer"), colorValueFromKt("md_theme_dark_onErrorContainer")) >= 4.5)
    }

    @Test
    fun errorStatusTextMeetsContrastOverScreenSurfaces() {
        // The error-role status texts sit on two real backgrounds: the Scaffold surface
        // (RecognizeActivity, TranscribeFileActivity) and the IME's surfaceContainerLow
        // floating panel (HandyInputMethodService). Both pairs must hold WCAG AA (>= 4.5:1)
        // for normal text, in both modes. Values derive from Color.kt (single source of
        // truth, like the canonical-baseline test).
        assertTrue(
            "light error must meet AA over surface",
            contrastRatio(colorValueFromKt("md_theme_light_error"), colorValueFromKt("md_theme_light_surface")) >= 4.5,
        )
        assertTrue(
            "light error must meet AA over surfaceContainerLow (IME panel)",
            contrastRatio(colorValueFromKt("md_theme_light_error"), colorValueFromKt("md_theme_light_surfaceContainerLow")) >= 4.5,
        )
        assertTrue(
            "dark error must meet AA over surface",
            contrastRatio(colorValueFromKt("md_theme_dark_error"), colorValueFromKt("md_theme_dark_surface")) >= 4.5,
        )
        assertTrue(
            "dark error must meet AA over surfaceContainerLow (IME panel)",
            contrastRatio(colorValueFromKt("md_theme_dark_error"), colorValueFromKt("md_theme_dark_surfaceContainerLow")) >= 4.5,
        )
    }

    @Test
    fun imeStatusTextPairingsMeetContrastOverPanel() {
        // The IME status line renders three roles over its surfaceContainerLow panel:
        // recording -> primary, error -> error, idle -> onSurfaceVariant. All must hold
        // WCAG AA (>= 4.5:1) for normal text in both modes (verified: light 5.84/5.93/8.47,
        // dark 10.02/10.00/10.02).
        val lightPanel = colorValueFromKt("md_theme_light_surfaceContainerLow")
        val darkPanel = colorValueFromKt("md_theme_dark_surfaceContainerLow")
        assertTrue("light primary (recording) over IME panel", contrastRatio(colorValueFromKt("md_theme_light_primary"), lightPanel) >= 4.5)
        assertTrue("light onSurfaceVariant (idle) over IME panel", contrastRatio(colorValueFromKt("md_theme_light_onSurfaceVariant"), lightPanel) >= 4.5)
        assertTrue("dark primary (recording) over IME panel", contrastRatio(colorValueFromKt("md_theme_dark_primary"), darkPanel) >= 4.5)
        assertTrue("dark onSurfaceVariant (idle) over IME panel", contrastRatio(colorValueFromKt("md_theme_dark_onSurfaceVariant"), darkPanel) >= 4.5)
    }

    @Test
    fun screenContrastPairsMeetWcagAa() {
        // Systematic sweep of every foreground/background pair actually rendered by the
        // app's screens, in both schemes (WCAG AA normal text >= 4.5:1). Values derive
        // from Color.kt (single source of truth).
        val pairs = listOf(
            "body text on scaffold" to ("onSurface" to "surface"),
            "secondary text on scaffold" to ("onSurfaceVariant" to "surface"),
            "default card content (surfaceContainerLow)" to ("onSurface" to "surfaceContainerLow"),
            "coming-soon card content (surfaceContainerHigh)" to ("onSurface" to "surfaceContainerHigh"),
            "model metadata on available card" to ("primary" to "surfaceContainerLow"),
            "model metadata on coming-soon card" to ("primary" to "surfaceContainerHigh"),
            "filled button" to ("onPrimary" to "primary"),
            "selected chip / segmented button" to ("onSecondaryContainer" to "secondaryContainer"),
            "brand hero card (primaryContainer)" to ("onPrimaryContainer" to "primaryContainer"),
            "recording stop button (IME)" to ("onErrorContainer" to "errorContainer"),
        )
        listOf("light", "dark").forEach { mode ->
            pairs.forEach { (label, roles) ->
                val (fg, bg) = roles
                val ratio = contrastRatio(
                    colorValueFromKt("md_theme_${mode}_$fg"),
                    colorValueFromKt("md_theme_${mode}_$bg"),
                )
                assertTrue("$mode: $label must meet WCAG AA ($fg on $bg, got ${String.format("%.2f", ratio)}:1)", ratio >= 4.5)
            }
        }
    }

    @Test
    fun componentsUseExpandedMd3Roles() {
        // The expanded roles must actually be used where they belong, not just defined:
        // - IME (floating panel) uses surfaceContainerLow for tonal elevation and the error
        //   role for error states (mic unavailable, failed transcription, missing permission).
        val ime = File("src/main/java/com/handy/android/HandyInputMethodService.kt").readText()
        assertTrue("IME surface must use surfaceContainerLow", ime.contains("colorScheme.surfaceContainerLow"))
        assertTrue("IME must surface error states with the error role", ime.contains("colorScheme.error"))
        assertTrue("IME must track error state", ime.contains("statusIsError"))

        // - Model store 'coming soon' cards use surfaceContainerHigh (surfaceVariant is
        //   deprecated as a container role in MD3).
        val models = File("src/main/java/com/handy/android/ModelsActivity.kt").readText()
        assertTrue("coming-soon cards must use surfaceContainerHigh", models.contains("colorScheme.surfaceContainerHigh"))
        assertFalse("surfaceVariant must not be used as a container in the model store", models.contains("colorScheme.surfaceVariant"))

        // - The brand hero cards must use the spec content pair for a primaryContainer
        //   surface (onPrimaryContainer), not the library default onSurface.
        val main = File("src/main/java/com/handy/android/MainActivity.kt").readText()
        assertTrue(
            "hero cards must use onPrimaryContainer content on primaryContainer",
            main.contains("contentColor = MaterialTheme.colorScheme.onPrimaryContainer"),
        )

        // - The voice-capture screens must surface their error states (mic unavailable,
        //   failed transcription, unreadable file) with the error role, like the IME.
        val recognize = File("src/main/java/com/handy/android/RecognizeActivity.kt").readText()
        assertTrue("Recognize must surface error states with the error role", recognize.contains("colorScheme.error"))
        assertTrue("Recognize must track error state", recognize.contains("statusIsError"))

        val transcribeFile = File("src/main/java/com/handy/android/TranscribeFileActivity.kt").readText()
        assertTrue("TranscribeFile must surface error states with the error role", transcribeFile.contains("colorScheme.error"))
        assertTrue("TranscribeFile must track error state", transcribeFile.contains("resultIsError"))

        // - Scrolling pages must collapse the top app bar (M3 enterAlwaysScrollBehavior)
        //   via the nested scroll connection, not a dead, disconnected scroll state.
        listOf(
            "RecognizeActivity.kt",
            "TranscribeFileActivity.kt",
            "LlmSettingsActivity.kt",
            "PostProcessSettingsActivity.kt",
            "TranscriptionSettingsActivity.kt",
            "LiveLogViewerActivity.kt",
        ).forEach { name ->
            val source = File("src/main/java/com/handy/android/$name").readText()
            assertTrue("$name must use enterAlwaysScrollBehavior", source.contains("enterAlwaysScrollBehavior()"))
            assertTrue("$name must wire the nested scroll connection", source.contains("scrollBehavior.nestedScrollConnection"))
        }

        // - The voice-capture screens use the large (expressive) top bar variant, like the
        //   model store, so the screen title reads as a headline that collapses on scroll.
        listOf(
            "RecognizeActivity.kt",
            "TranscribeFileActivity.kt",
            "ModelsActivity.kt",
        ).forEach { name ->
            val source = File("src/main/java/com/handy/android/$name").readText()
            assertTrue("$name must use LargeTopAppBar", source.contains("LargeTopAppBar"))
        }
    }

    @Test
    fun topBarVariantsFollowScreenHierarchy() {
        // M3 hierarchy decision (documented in AUDIT.md): the three destination/hero
        // screens get the large (expressive) top bar; every other screen keeps the
        // standard small bar, and Medium is intentionally unused — no screen sits
        // between the small toolbar and the large headline.
        val heroScreens = listOf(
            "ModelsActivity.kt",
            "RecognizeActivity.kt",
            "TranscribeFileActivity.kt",
        )
        heroScreens.forEach { name ->
            val source = File("src/main/java/com/handy/android/$name").readText()
            // Usage (with call parens), not just the import/type name.
            assertTrue("$name (destination/hero) must use LargeTopAppBar", source.contains("LargeTopAppBar("))
        }

        // Standard bar stays on: the navigation hub (small bar is the M3 companion of
        // NavigationSuiteScaffold), the dictionary editor (tool screen whose editor
        // fills the space), the short feature-control screen, and the settings/tool
        // pages (which already collapse via enterAlwaysScrollBehavior).
        val standardScreens = listOf(
            "MainActivity.kt",
            "CustomWordsActivity.kt",
            "LiveSubtitleActivity.kt",
            "LlmSettingsActivity.kt",
            "PostProcessSettingsActivity.kt",
            "TranscriptionSettingsActivity.kt",
            "LiveLogViewerActivity.kt",
        )
        standardScreens.forEach { name ->
            val source = File("src/main/java/com/handy/android/$name").readText()
            // Usage with call parens, so a comment or import cannot false-fail.
            assertFalse("$name must keep the standard top bar", source.contains("LargeTopAppBar("))
            assertFalse("$name must keep the standard top bar (no Medium variant)", source.contains("MediumTopAppBar("))
        }
    }

    @Test
    fun dynamicColorSettingDefaultsToFalseAndRoundTrips() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SettingsManager.setDynamicColorEnabled(context, false)
        assertFalse("dynamic color must default to off (brand palette)", SettingsManager.dynamicColorEnabled(context))

        SettingsManager.setDynamicColorEnabled(context, true)
        assertTrue("dynamic color must persist once enabled", SettingsManager.dynamicColorEnabled(context))

        SettingsManager.setDynamicColorEnabled(context, false)
        assertFalse("dynamic color must persist once disabled", SettingsManager.dynamicColorEnabled(context))
    }

    private fun colorValueFromXml(name: String): String {
        val regex = Regex("""<color name="$name">#([0-9A-Fa-f]{6,8})</color>""")
        val match = regex.find(colorsXml) ?: error("missing color $name in colors.xml")
        return match.groupValues[1].uppercase()
    }

    private fun colorValueFromKt(name: String): String {
        val regex = Regex("""val $name = Color\(0x([0-9A-Fa-f]{8})\)""")
        val match = regex.find(colorKt) ?: error("missing color $name in Color.kt")
        // Color.kt uses 0xAARRGGBB; colors.xml uses #RRGGBB (alpha FF implied).
        return match.groupValues[1].substring(2).uppercase()
    }

    private fun luminanceOf(rgbHex: String): Double {
        val r = rgbHex.substring(0, 2).toInt(16) / 255.0
        val g = rgbHex.substring(2, 4).toInt(16) / 255.0
        val b = rgbHex.substring(4, 6).toInt(16) / 255.0
        fun linearize(channel: Double): Double =
            if (channel <= 0.03928) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
        return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
    }

    private fun contrastRatio(aRgb: String, bRgb: String): Double {
        val la = luminanceOf(aRgb)
        val lb = luminanceOf(bRgb)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
