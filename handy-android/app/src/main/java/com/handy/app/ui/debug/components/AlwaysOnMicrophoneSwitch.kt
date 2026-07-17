package com.handy.app.ui.debug.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandySwitchRow

/**
 * Sprint 28b — toggles `always_on_microphone`. ON keeps the AAudio
 * capture hot between dictations so the next utterance does not pay
 * pre-roll latency; OFF yields the mic back to other apps faster.
 * Default OFF = production behavior.
 */
@Composable
fun AlwaysOnMicrophoneSwitch(
    modifier: Modifier = Modifier,
    app: HandyApplication,
    enabled: Boolean = true,
) {
    val checked by app.settingsStore.alwaysOnMicrophoneFlow.collectAsState()
    HandySwitchRow(
        title = stringResource(R.string.debug_audio_alwaysonmicrophone_label),
        subtitle = stringResource(R.string.debug_audio_alwaysonmicrophone_subtitle),
        leadingIcon = Icons.Default.Mic,
        checked = checked,
        onCheckedChange = { app.settingsStore.alwaysOnMicrophone = it },
        modifier = modifier,
        enabled = enabled,
    )
}
