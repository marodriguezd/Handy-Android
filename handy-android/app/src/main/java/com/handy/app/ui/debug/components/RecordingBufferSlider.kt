package com.handy.app.ui.debug.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandySlider

/**
 * Sprint 28b — slider for `recording_buffer_frames`. The slider
 * emits an Int in `8192..1_048_576` (8 K frames min, 1 M max, 4096-
 * aligned); the setter snaps to the nearest 4096 multiple so the
 * AAudio allocator is happy. Default 262_144 matches production
 * (≈ 16 s at 16 kHz mono).
 */
@Composable
fun RecordingBufferSlider(
    modifier: Modifier = Modifier,
    app: HandyApplication,
    enabled: Boolean = true,
) {
    val value by app.settingsStore.recordingBufferFramesFlow.collectAsState()
    HandySlider(
        value = value.toFloat(),
        onValueChange = { app.settingsStore.recordingBufferFrames = it.toInt() },
        label = stringResource(R.string.debug_audio_recordingbuffer_label),
        formatValue = { v -> "${v.toInt() / 1024}K frames" },
        valueRange = 8192f..1_048_576f,
        steps = 0,
        enabled = enabled,
        // Standalone slider in Debug screen: fill the parent width.
        modifier = modifier.fillMaxWidth(),
    )
}
