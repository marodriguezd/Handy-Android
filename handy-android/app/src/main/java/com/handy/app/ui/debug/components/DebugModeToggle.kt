package com.handy.app.ui.debug.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandySwitchRow

/**
 * Sprint 28b — first row on the Debug screen; lets a developer
 * disable the gate without leaving the screen. The flip propagates
 * via [com.handy.app.SettingsStore.debugModeFlow] and
 * `AppNavigation`'s `debugEnabled` parameter rebuilds the nav list
 * (Option A — screen never disappears mid-frame, in-flight pass
 * renders the "Developer tools off" placeholder until next launch).
 */
@Composable
fun DebugModeToggle(
    modifier: Modifier = Modifier,
    app: HandyApplication,
    enabled: Boolean = true,
) {
    val checked by app.settingsStore.debugModeFlow.collectAsState()
    HandySwitchRow(
        title = stringResource(R.string.debug_mode_toggle_label),
        subtitle = stringResource(R.string.debug_mode_toggle_subtitle),
        leadingIcon = Icons.Default.Code,
        checked = checked,
        onCheckedChange = { app.settingsStore.debugMode = it },
        modifier = modifier,
        enabled = enabled,
    )
}
