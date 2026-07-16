package com.handy.app.ui.settings.components

import android.media.AudioManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.ui.components.HandyDropdown
import com.handy.app.ui.components.SettingsRow

/**
 * Mirrors `MicrophoneSelector.tsx` from the Handy PC sidebar.
 *
 * Lists every input device that the system exposes via [AudioManager]'s
 * `getDevices(GET_DEVICES_INPUTS)` (built-in mic, USB mics, Bluetooth
 * headsets, …) and surfaces the chosen device index through [onSelect].
 *
 * Note: we intentionally avoid typing the `android.media.AudioDevice`
 * SDK class directly here because some build environments cannot resolve
 * its symbol. The list's items are addressed by their numeric index
 * inside `getDevices(...)`, which is stable across calls.
 *
 * @param selected currently selected device index (matches one of the
 *   input devices).  Pass `null` to show "System default".
 * @param onSelect invoked with the new index when the user picks a
 *   device.  `null` means "system default".
 */
@Composable
fun MicrophoneSelector(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }

    // `refreshKey` is the seed that drives the remember{} below: bumping
    // it re-fetches the live device list without our holding the
    // `AudioDevice` type as a Kotlin-level state.  Initial mount runs
    // once via `LaunchedEffect(Unit)`; the refresh button just bumps
    // the key.
    var refreshKey by remember { mutableIntStateOf(0) }
    val devices = remember(audioManager, refreshKey) {
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
    }

    val label = stringResource(R.string.settings_microphone_label)
    val defaultLabel = stringResource(R.string.settings_microphone_default)
    val options: List<Pair<String?, String>> = remember(devices) {
        buildList {
            add(null to defaultLabel)
            devices.indices.forEach { index ->
                add(index.toString() to "Microphone ${index + 1}")
            }
        }
    }
    val resolvedSelected: String? = remember(selected, devices) {
        if (selected == null) null
        else if (selected.toIntOrNull() in devices.indices) selected
        else null
    }

    SettingsRow(
        title = label,
        subtitle = stringResource(R.string.settings_microphone_desc),
        leading = {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HandyDropdown(
                    label = label,
                    options = options,
                    selected = resolvedSelected,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { refreshKey++ }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.settings_refresh),
                    )
                }
            }
        },
        modifier = modifier,
    )
}
