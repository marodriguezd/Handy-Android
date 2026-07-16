package com.handy.app.ui.settings.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.HandyDropdown
import com.handy.app.ui.components.SettingsRow

/**
 * Pick which start/stop sound Handy plays when dictation starts and
 * stops (the marimba / pop-start pair bundled in `src-tauri/resources/`).
 *
 * PC equivalent: `SoundPicker.tsx` from the sidebar. Only the three
 * bundled PC themes are available on Android.
 */
@Composable
fun SoundPicker(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val title = stringResource(R.string.settings_sound_picker_label)
    val desc = stringResource(R.string.settings_sound_picker_desc)
    val options: List<Pair<String, String>> = listOf(
        "handy_normal" to stringResource(R.string.settings_sound_normal),
        "handy_soft" to stringResource(R.string.settings_sound_soft),
        "handy_narrator" to stringResource(R.string.settings_sound_narrator),
    )
    SettingsRow(
        title = title,
        subtitle = desc,
        leading = {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            HandyDropdown(
                label = title,
                options = options,
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}
