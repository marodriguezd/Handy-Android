package com.handy.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * SettingsGroup — compact MD3 section card for the redesigned settings screen.
 *
 * Redesign goals (2026-07-22):
 *  - Reduce the visual weight and padding of the previous tonal-card group.
 *  - Use a subtle outlined card (`surfaceContainerLow` + `outlineVariant`) so
 *    the section reads as one surface without dominating the screen.
 *  - Keep the section title tight and optionally add a leading icon for faster
 *    visual scanning.
 *  - Let the parent scrolling container handle outer margins; the group itself
 *    fills the width it is given.
 *
 * @param title Section title displayed above the card.
 * @param modifier Modifier applied to the outer [Column].
 * @param icon Optional leading icon shown next to the title, tinted primary.
 * @param subtitle Optional smaller description shown below the title.
 */
@Composable
@Suppress("ModifierParameter")
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = Spacing.sm),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = Spacing.sm),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
        }
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
