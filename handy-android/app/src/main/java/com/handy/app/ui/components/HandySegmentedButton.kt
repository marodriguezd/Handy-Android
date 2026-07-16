package com.handy.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * MD3-native [SingleChoiceSegmentedButtonRow] wrapper.  Renders 2..5
 * segments and selects the one matching [selected].  Used by the About
 * Theme selector (System / Light / Dark) and any future 2-3 way toggle.
 *
 * @param options: (key, label) pairs.
 * @param selected: currently selected key.
 * @param onSelect: invoked with the new key.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> HandySegmentedButton(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { if (enabled) onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                    inactiveBorderColor = MaterialTheme.colorScheme.outline,
                ),
                enabled = enabled,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
