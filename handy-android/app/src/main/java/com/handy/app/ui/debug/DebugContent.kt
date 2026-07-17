package com.handy.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.SettingsGroup
import com.handy.app.ui.debug.components.AlwaysOnMicrophoneSwitch
import com.handy.app.ui.debug.components.DebugModeToggle
import com.handy.app.ui.debug.components.LiveLogViewer
import com.handy.app.ui.debug.components.LogLevelSelector
import com.handy.app.ui.debug.components.PasteDelaySlider
import com.handy.app.ui.debug.components.RecordingBufferSlider
import com.handy.app.ui.debug.components.UpdateChecksToggle
import com.handy.app.ui.settings.components.SoundPicker

/**
 * Sprint 28b — Debug panel MD3 composition root (full implementation).
 *
 * Sprint 28 MVP shipped placeholder rows so reviewers could see the
 * group/row grid; this is the real component graph:
 *
 *   - DebugModeToggle (first row — lets a developer turn off the
 *     gate without leaving the screen).
 *   - Logging: LogLevelSelector + LiveLogViewer.
 *   - Updates & Info: UpdateChecksToggle + a "Preview What's New"
 *     placeholder row (the modal exists in `whats-new/`; Sprint 28b
 *     scopes to the wiring so the surface stays).
 *   - Audio: SoundPicker, PasteDelaySlider, RecordingBufferSlider,
 *     AlwaysOnMicrophoneSwitch.
 *
 * The SoundPicker row is `ui.settings.components.SoundPicker` (the
 * same primitive used in General Settings) — single source of truth
 * so changing the sound list re-emits in both screens.
 *
 * Gated by `Settings.debugMode == true` via `AppNavigation.debugEnabled`
 * (reactive via `debugModeFlow.collectAsState()` in MainActivity,
 * Option A: the Debug route is always registered, the placeholder
 * body renders when the gate is false).
 */
@Composable
fun DebugScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as HandyApplication

    DebugContent(app)
}

@Composable
internal fun DebugContent(app: HandyApplication) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.debug_screen_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        DebugModeToggle(app = app)

        SettingsGroup(title = stringResource(R.string.debug_section_logging)) {
            LogLevelSelector(app = app)
            LiveLogViewer(app = app)
        }

        SettingsGroup(title = stringResource(R.string.debug_section_updates)) {
            UpdateChecksToggle(app = app)
            // WhatsNewPreview is intentionally deferred; the modal
            // exists at `ui/whats-new/` but no SettingsStore flag
            // drives it from the Debug panel yet. Sprint 28b keeps the
            // row visible so the surface doesn't move on Sprint 28c.
            Text(
                text = "${stringResource(R.string.debug_whatsnew_label)} (Sprint 28c)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        SettingsGroup(title = stringResource(R.string.debug_section_audio)) {
            SoundPicker(
                selected = app.settingsStore.soundTheme,
                onSelect = { app.settingsStore.soundTheme = it },
                enabled = app.settingsStore.audioFeedbackEnabled,
            )
            PasteDelaySlider(app = app)
            RecordingBufferSlider(app = app)
            AlwaysOnMicrophoneSwitch(app = app)
        }

        Text(
            text = stringResource(R.string.debug_panel_gated_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Public, no-op body used when [com.handy.app.navigation.AppNavigation]
 * registers the Debug route but the gate is currently OFF (Option A
 * for the Sprint 28b reactive-flag-flip hardening — see the comment
 * block above [com.handy.app.navigation.AppNavigation.AppNavigation]).
 */
@Composable
fun DeveloperToolsDisabled() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
