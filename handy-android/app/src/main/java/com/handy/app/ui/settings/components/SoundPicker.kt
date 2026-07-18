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
            // Sprint 30c-#5: same root cause as MicrophoneSelector's trailing
            // slot — see that file's KDoc block for the full diagnosis.
            // Briefly: `Modifier.fillMaxWidth()` here is the trailing slot's
            // child of HandyListItem's OUTER Row. The Row distributes width
            // greedily to non-`weight(1f)` children first; an unweighted
            // child that asks for `fillMaxWidth()` consumes the full Row
            // max-width and starves the title/subtitle `Column(weight(1f))`
            // of the middle column, producing 0dp width → Text wraps one
            // character per line → astronomically tall Surface → items
            // pushed off-screen with massive empty dark gaps.
            //
            // Removing `fillMaxWidth()` here lets the HandyDropdown report
            // its INTRINSIC width (= the OutlinedTextField's natural
            // render width ~250dp for "Marimba"/"Pop"/"Narrador"), and
            // the title/subtitle column retains the remainder.
            HandyDropdown(
                label = title,
                options = options,
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}
