package com.handy.app.ui.debug.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandySlider

/**
 * Sprint 28b — user-tunable delay between clipboard push and
 * paste-key injection. The slider emits an Int in 0..1000 ms;
 * ShizukuInjector.convert reads [com.handy.app.SettingsStore.pasteDelayMs]
 * on each `inject()` call so the change takes effect immediately
 * (no restart needed). Default 50 ms = historical ShizukuInjector
 * hardcode.
 */
@Composable
fun PasteDelaySlider(
    modifier: Modifier = Modifier,
    app: HandyApplication,
    enabled: Boolean = true,
) {
    val value by app.settingsStore.pasteDelayMsFlow.collectAsState()
    HandySlider(
        value = value.toFloat(),
        onValueChange = { app.settingsStore.pasteDelayMs = it.toInt() },
        label = stringResource(R.string.debug_audio_pastedelay_label),
        formatValue = { v -> "${v.toInt()} ms" },
        valueRange = 0f..1000f,
        steps = 19, // 0, 50, 100, ... 1000
        enabled = enabled,
        modifier = modifier,
    )
}
