package com.handy.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
 * Backwards-compat delegation to [HandyListItem]. The implementation
 * moved to `HandyListItem.kt` in Sprint 25; existing callers in
 * `AboutContent.kt` + `SettingsScreen.kt` keep the `SettingsRow` name
 * unchanged. New code should prefer `HandyListItem` directly.
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
) = HandyListItem(
    title = title,
    subtitle = subtitle,
    leading = leading,
    trailing = trailing,
    onClick = onClick,
    modifier = modifier,
)

/**
 * Backwards-compat delegation to [HandyListItemDivider]. See
 * [SettingsRow] for the migration story.
 */
@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) =
    HandyListItemDivider(modifier = modifier)

