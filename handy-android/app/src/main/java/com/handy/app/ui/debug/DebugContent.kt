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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.ui.components.SettingsGroup

/**
 * Sprint 28 — Debug panel MD3 composition root.
 *
 * Gated by `Settings.debugMode == true` (set via SettingsStore.debugMode).
 * The `Screen.Debug` nav-rail/bar item only appears when the flag is
 * true; this content is never reachable in release builds unless the
 * developer explicitly opts in.
 *
 * Sprint 28 MVP scope:
 *   - 3 placeholder SettingsGroups covering Logging, Updates, and Audio.
 *   - 7 placeholder rows surface the future component shape so reviewers
 *     can see what's planned. Each row is a [PlaceholderText] using a
 *     string resource — when the component lands in Sprint 28b, the
 *     string content stays and the row body swaps from "coming soon"
 *     text to the actual HandySwitch / HandyDropdown / HandySlider.
 *   - A gated-hint footer reminds the user this is developer tooling.
 *
 * Future Sprint 28b implementation plan:
 *   - LogLevelSelector (HandyDropdown: VERBOSE/DEBUG/INFO/WARN/ERROR)
 *   - UpdateChecksToggle (HandySwitch)
 *   - SoundPicker (reuse from ui.settings.components.SoundPicker)
 *   - PasteDelaySlider (HandySlider 0..1000 ms)
 *   - RecordingBufferSlider (HandySlider 0..600 s)
 *   - AlwaysOnMicrophoneSwitch (HandySwitch)
 *   - LiveLogViewer (LazyColumn + com.handy.app.util.RingBufferLog tail(50))
 */
@Composable
fun DebugContent() {
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

        SettingsGroup(title = stringResource(R.string.debug_section_logging)) {
            PlaceholderText(R.string.debug_log_loglevel_label)
            PlaceholderText(R.string.debug_log_liveviewer_label)
        }

        SettingsGroup(title = stringResource(R.string.debug_section_updates)) {
            PlaceholderText(R.string.debug_updates_check_label)
            PlaceholderText(R.string.debug_whatsnew_label)
        }

        SettingsGroup(title = stringResource(R.string.debug_section_audio)) {
            PlaceholderText(R.string.debug_audio_soundpicker_label)
            PlaceholderText(R.string.debug_audio_pastedelay_label)
            PlaceholderText(R.string.debug_audio_recordingbuffer_label)
            PlaceholderText(R.string.debug_audio_alwaysonmicrophone_label)
        }

        Text(
            text = stringResource(R.string.debug_panel_gated_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PlaceholderText(labelResId: Int) {
    val label = stringResource(labelResId)
    val suffix = stringResource(R.string.debug_placeholder_suffix)
    Text(
        text = "$label $suffix",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
