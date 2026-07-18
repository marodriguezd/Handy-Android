package com.handy.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandySnackbarHost
import com.handy.app.ui.components.HandySnackbarHostState
import com.handy.app.ui.components.SettingsGroup
import com.handy.app.ui.debug.components.AlwaysOnMicrophoneSwitch
import com.handy.app.ui.debug.components.DebugModeToggle
import com.handy.app.ui.debug.components.LiveLogViewer
import com.handy.app.ui.debug.components.LogLevelSelector
import com.handy.app.ui.debug.components.PasteDelaySlider
import com.handy.app.ui.debug.components.RecordingBufferSlider
import com.handy.app.ui.debug.components.RecordingDualWriteToggle
import com.handy.app.ui.debug.components.UpdateChecksToggle
import com.handy.app.ui.settings.components.SoundPicker
import kotlinx.coroutines.launch

/**
 * Sprint 28b-v14 — Debug panel MD3 composition root (full implementation).
 *
 * # History (regression arc)
 * - Sprint 28 MVP shipped placeholder rows so the group/row grid was visible.
 * - Sprint 28b replaced them with real components (LogLevelSelector,
 *   LiveLogViewer, UpdateChecksToggle, SoundPicker, PasteDelaySlider,
 *   RecordingBufferSlider, AlwaysOnMicrophoneSwitch).
 * - Sprint 28b-v11 wired `DebugModeToggle.onFlip` → in-screen Snackbar
 *   feedback via `HandySnackbarHost`.
 * - Sprint 28b-v12 attempted `Scaffold(modifier = Modifier.fillMaxSize(),
 *   snackbarHost = …)`. On-device repro on Android 16 (A059) proved this
 *   fix insufficient — Compose Material3 Scaffold internally uses
 *   `SubcomposeLayout`, which drops `Constraints.Infinity` into the content
 *   slot during the parent's enter-transition measure phase.
 * - Sprint 28b-v13 replaced Scaffold with `Box(Modifier.fillMaxSize())`.
 *   On-device repro STILL failed — Compose Navigation's NavHost wraps each
 *   destination in `AnimatedContent`, whose measurement phase provides
 *   `Constraints.Infinity` for maxHeight regardless of the host's
 *   `Modifier.fillMaxSize()`. The inner
 *   `Column(fillMaxSize).verticalScroll(...)` still received infinity and
 *   tripped Compose's runtime check:
 *   "Vertically scrollable component was measured with an infinity maximum
 *   height constraints, which is disallowed."
 * - **Sprint 28b-v14 (this file)** replaces the scrollable Column with
 *   `LazyColumn`. LazyColumn's measure policy is fundamentally different —
 *   it measures only items currently in the viewport, not the full
 *   intrinsic content height — so it does NOT require bounded maxHeight.
 *   AnimatedContent's Infinity propagation no longer crashes Compose.
 *
 * # Surface
 * The screen still renders the full Debug component graph:
 *   - DebugModeToggle first row (developer-facing gate flipping).
 *   - Logging group: LogLevelSelector + LiveLogViewer.
 *   - Updates & Info group: UpdateChecksToggle + a "Preview What's New"
 *     placeholder row (`ui.whats-new/` exists but not yet wired to a
 *     SettingsStore flag).
 *   - Audio group: SoundPicker reuse (single-source-of-truth with the
 *     General Settings surface), PasteDelaySlider, RecordingBufferSlider,
 *     AlwaysOnMicrophoneSwitch.
 *
 * # Wiring contract
 * Gated by `Settings.debugMode == true` via `AppNavigation.debugEnabled`
 * (set reactively in `MainActivity` via `debugModeFlow.collectAsState()`,
 * Option A: the Debug route is always registered; the
 * `DeveloperToolsDisabled()` placeholder body renders when the gate is
 * false so the NavHost graph never tears).
 *
 * @see DebugLayoutRegressionTest for the JVM Compose UI test pinning the
 *   LazyColumn contract against future regressions.
 */
@Composable
fun DebugScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as HandyApplication
    // Sprint 28b-v11 — host the Snackbar at the screen level so the
    // post-flip feedback is visible whether or not the user is currently
    // scrolled at the top of the DebugContent. `remember` keeps the same
    // host state across recompositions.
    val snackbar = remember { HandySnackbarHostState() }

    Box(modifier = Modifier.fillMaxSize()) {
        DebugContent(
            app = app,
            snackbar = snackbar,
            modifier = Modifier,
        )
        HandySnackbarHost(
            state = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
internal fun DebugContent(
    app: HandyApplication,
    snackbar: HandySnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val enabledMessage = stringResource(R.string.debug_toggle_enabled_feedback)
    val disabledMessage = stringResource(R.string.debug_toggle_disabled_feedback)

    // Sprint 28b-v14 — LazyColumn replaces Column.verticalScroll.
    // LazyColumn's measure policy measures only visible items in the
    // viewport, so an Infinity maxHeight from the parent is acceptable.
    // Each SettingsGroup + leading Text + trailing hint text is its own
    // LazyListScope item so individual visual units retain their natural
    // spacing (Arrangement.spacedBy(16.dp)).
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.debug_screen_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            // Sprint 28b-v11 — onFlip callback fires after the toggle writes
            // to settingsStore.debugMode. The parent observes via this callback
            // (not via collectAsState) to avoid a sentinel-first-composition
            // false positive in LaunchedEffect. The callback picks the pre-resolved
            // String locally; the resource-ID mapping stays JVM-testable via
            // `getSnackbarMessageForFlip(newValue)` in DebugPresentation.kt.
            DebugModeToggle(
                app = app,
                onFlip = { newValue ->
                    scope.launch {
                        snackbar.showMessage(if (newValue) enabledMessage else disabledMessage)
                    }
                },
            )
        }

        item {
            SettingsGroup(title = stringResource(R.string.debug_section_logging)) {
                LogLevelSelector(app = app)
                LiveLogViewer(app = app)
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.debug_section_updates)) {
                UpdateChecksToggle(app = app)
                // WhatsNewPreview is intentionally deferred; the modal
                // exists at `ui.whats-new/` but no SettingsStore flag
                // drives it from the Debug panel yet. Sprint 28b keeps the
                // row visible so the surface doesn't move on Sprint 28c.
                Text(
                    text = "${stringResource(R.string.debug_whatsnew_label)} (Sprint 28c)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.debug_section_audio)) {
                SoundPicker(
                    selected = app.settingsStore.soundTheme,
                    onSelect = { app.settingsStore.soundTheme = it },
                    enabled = app.settingsStore.audioFeedbackEnabled,
                )
                PasteDelaySlider(app = app)
                RecordingBufferSlider(app = app)
                AlwaysOnMicrophoneSwitch(app = app)
                RecordingDualWriteToggle(app = app)
            }
        }

        item {
            Text(
                text = stringResource(R.string.debug_panel_gated_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Public, no-op body used when [com.handy.app.navigation.AppNavigation]
 * registers the Debug route but the gate is currently OFF (Option A
 * for the Sprint 28b reactive-flag-flip hardening — see the comment
 * block above [com.handy.app.navigation.AppNavigation.AppNavigation]).
 *
 * The placeholder body remains `Box(fillMaxSize) + Column(…)` shape
 * (Sprint 28b-v13) — short, intrinsics-only content that does not trip
 * the AnimatedContent measure-pass issue because the inner Column has no
 * verticalsScroll modifier.
 */
@Composable
fun DeveloperToolsDisabled() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        // Inner Column's fillMaxSize preserves the original shape
        // (Sprint 28b-v12): the column grows to fill the Box so any
        // future row added to this placeholder bottoms up predictably
        // rather than collapsing. Visual is identical with two Text
        // children but the modifier is shape-stable.
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.debug_screen_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.debug_screen_disabled_body),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
