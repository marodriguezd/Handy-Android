package com.handy.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * MD3-native [FilterChip] group.  Backed by [LazyRow] so it scrolls when
 * the list of options is larger than the screen — used by the Models
 * catalog's language filter and the Recommendation toggle.
 *
 * @param options: (key, label) pairs.
 * @param selected: currently selected key (single-select).
 * @param onSelect: invoked with the new key when a chip is tapped; tapping
 *   the already-selected chip is a no-op.
 * @param leadingIcon: optional icon shown inside each chip.
 */
@Composable
fun <T> HandyChipGroup(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = Spacing.sm),
            )
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
        ) {
            items(options) { (value, text) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { if (enabled && value != selected) onSelect(value) },
                    label = { Text(text = text) },
                    leadingIcon = if (leadingIcon != null) {
                        {
                            androidx.compose.material3.Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    enabled = enabled,
                )
            }
        }
    }
}
