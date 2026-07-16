package com.handy.app.ui.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.HandySlider
import com.handy.app.ui.components.SettingsRow

/**
 * Audio-feedback volume slider.  Mirrors PC `VolumeSlider.tsx` from
 * the sidebar: a 0..1 value rendered as a percentage.
 */
@Composable
fun VolumeSlider(
    volume: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val title = stringResource(R.string.settings_volume_label)
    val desc = stringResource(R.string.settings_volume_desc)
    SettingsRow(
        title = title,
        subtitle = desc,
        leading = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            HandySlider(
                value = volume.coerceIn(0f, 1f),
                onValueChange = onValueChange,
                label = title,
                formatValue = { v -> "${(v * 100).toInt()}%" },
                valueRange = 0f..1f,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        modifier = modifier,
    )
}
