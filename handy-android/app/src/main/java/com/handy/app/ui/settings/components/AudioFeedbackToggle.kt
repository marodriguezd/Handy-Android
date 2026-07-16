package com.handy.app.ui.settings.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.HandySwitchRow

/**
 * "Play a sound when starting / stopping dictation" toggle.  Equivalent
 * to PC `AudioFeedback.tsx`.  Use [HandySwitchRow] directly; this
 * wrapper just centralizes the strings and the leading icon.
 */
@Composable
fun AudioFeedbackToggle(
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.settings_audio_feedback_label)
    HandySwitchRow(
        title = title,
        subtitle = stringResource(R.string.settings_audio_feedback_desc),
        leadingIcon = Icons.AutoMirrored.Filled.VolumeUp,
        checked = enabled,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
    )
}
