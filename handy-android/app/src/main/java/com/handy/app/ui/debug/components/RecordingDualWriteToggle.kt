package com.handy.app.ui.debug.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandySwitchRow

@Composable
fun RecordingDualWriteToggle(
    modifier: Modifier = Modifier,
    app: HandyApplication,
    enabled: Boolean = true,
) {
    val checked by app.settingsStore.recordingDualWriteFlow.collectAsState()
    HandySwitchRow(
        title = stringResource(R.string.debug_audio_dualwrite_label),
        subtitle = stringResource(R.string.debug_audio_dualwrite_subtitle),
        leadingIcon = Icons.Default.Save,
        checked = checked,
        onCheckedChange = { app.settingsStore.recordingDualWriteMode = it },
        modifier = modifier,
        enabled = enabled,
    )
}
