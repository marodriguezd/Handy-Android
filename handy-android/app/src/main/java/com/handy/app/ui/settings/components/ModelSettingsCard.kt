package com.handy.app.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.ui.components.HandyTonalBlock
import com.handy.app.ui.components.HandyTonalCard
import com.handy.app.ui.components.SettingsRow
import com.handy.app.ui.components.TonalElevation

/**
 * Model settings card — shows the current downloaded model with a
 * quick "Unload" button.  Mirrors PC `ModelSettingsCard.tsx` from the
 * sidebar (current model id + capacity-bound unload).
 *
 * @param currentModelId id of the currently-active model.  `null` when
 *   no model is downloaded.
 * @param onUnload invoked when the user taps "Unload now".
 */
@Composable
fun ModelSettingsCard(
    currentModelId: String?,
    onUnload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardTitle = stringResource(R.string.settings_model_section_label)
    SettingsRow(
        title = cardTitle,
        subtitle = stringResource(R.string.settings_model_section_desc),
        leading = {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = currentModelId
                        ?: stringResource(R.string.settings_no_active_model),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (currentModelId == null)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onUnload,
                        enabled = currentModelId != null,
                    ) {
                        Text(stringResource(R.string.settings_unload_model))
                    }
                }
            }
        },
        modifier = modifier,
    )
}
