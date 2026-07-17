package com.handy.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * MD3-native [ListItem] wrapper.
 *
 * Sprint 18 declared `HandyListItem.kt` in the migration plan but the
 * implementation landed inline inside `SettingsGroup.kt` as
 * `SettingsRow` + `SettingsRowDivider`. Sprint 25 finally extracts the
 * primitive to its own file under the canonical `HandyListItem` /
 * `HandyListItemDivider` names.
 *
 * Backwards compat: `SettingsRow` / `SettingsRowDivider` in
 * `SettingsGroup.kt` now delegate to this file so existing call sites
 * (AboutContent.kt, SettingsScreen.kt) keep working without churn.
 * New code should prefer `HandyListItem` directly. The `Settings*`
 * aliases are kept only as a migration aid — future sprints may retire
 * them entirely.
 *
 *  - 48 dp ripple when `onClick != null`; read-only when null.
 *  - Tone = `surfaceContainerHigh` so each row reads as a distinct
 *    surface against the parent `SettingsGroup` card.
 *  - Title typography = `bodyLarge` + `onSurface`.
 *  - Subtitle (when present) = `bodyMedium` + `onSurfaceVariant`.
 */
@Composable
@Suppress("ModifierParameter")
fun HandyListItem(
    title: String,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = subtitle?.let {
            @Composable {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingContent = leading,
        trailingContent = trailing,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

/**
 * Convenience divider placed between [HandyListItem] rows. Uses MD3
 * [HorizontalDivider] tinted to `outlineVariant` so dark/light follow
 * automatically.
 */
@Composable
fun HandyListItemDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
