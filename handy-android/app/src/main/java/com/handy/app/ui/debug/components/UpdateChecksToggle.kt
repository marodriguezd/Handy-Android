package com.handy.app.ui.debug.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandySwitchRow

/**
 * Sprint 28b — toggle for `update_checks_on_launch`. When OFF, the
 * launch-time `UpdateChecker` is short-circuited so offline / CI
 * runs stay deterministic. Default ON in production.
 */
@Composable
fun UpdateChecksToggle(
    modifier: Modifier = Modifier,
    app: HandyApplication,
    enabled: Boolean = true,
) {
    val checked by app.settingsStore.updateChecksOnLaunchFlow.collectAsState()
    HandySwitchRow(
        title = stringResource(R.string.debug_updates_check_label),
        subtitle = stringResource(R.string.debug_updates_check_subtitle),
        leadingIcon = Icons.Default.SystemUpdate,
        checked = checked,
        onCheckedChange = { app.settingsStore.updateChecksOnLaunch = it },
        modifier = modifier,
        enabled = enabled,
    )
}
