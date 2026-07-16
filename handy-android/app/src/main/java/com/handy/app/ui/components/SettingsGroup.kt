package com.handy.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
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
 * MD3-native grouped setting.  Wraps an MD3 [ListItem] with sane defaults
 * so every settings row uses the same typography, surface tint and the
 * 48 dp touch target the Material 3 spec requires.
 *
 * @param title row title (bodyLarge).
 * @param subtitle supporting text.  Renders in `bodyMedium` +
 *   `onSurfaceVariant`.
 * @param leading optional leading icon or widget.
 * @param trailing optional trailing widget (Switch, Slider, etc.).
 * @param onClick when non-null the row becomes clickable (48 dp ripple).
 */
@Composable
@Suppress("ModifierParameter")
fun SettingsRow(
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
 * SettingsGroup — a titled tonal card that stacks MD3 setting rows.
 *
 * Replaces the previous manual `SectionHeader` + `HorizontalDivider`
 * pattern.  Wraps the rows in a [HandyTonalCard] so the group reads as
 * a distinct surface element against the parent (matches the PC
 * `SettingsGroup` component from the Handy desktop sidebar).
 */
@Composable
@Suppress("ModifierParameter")
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = Spacing.lg,
                top = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.sm,
            ),
        )
        HandyTonalCard(
            level = TonalElevation.Low,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                content()
            }
        }
    }
}

/**
 * Convenience divider placed between [SettingsRow] items within a
 * [SettingsGroup]. Uses MD3 [HorizontalDivider] tinted to
 * `outlineVariant` so it follows dark/light automatically.
 */
@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
