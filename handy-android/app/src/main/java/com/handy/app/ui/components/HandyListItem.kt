package com.handy.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * MD3-native list item wrapper.
 *
 * **Sprint 30c-#4 (2026-07-18) — REPLACES M3 `ListItem` with custom Row+Column
 * because the original M3 wrapper introduced an unrecoverable crash**:
 *
 *   `IllegalArgumentException: maxWidth(-83) must be >= than minWidth(0)`
 *   at `androidx.compose.material3.ListItemMeasurePolicy.measure-3p2s80s(ListItem.kt:234)`
 *
 * M3 1.3.1's [androidx.compose.material3.ListItem] internally uses a
 * [androidx.compose.ui.layout.MultiContentMeasurePolicy] (SubcomposeLayout)
 * to lay out leading / headline / supporting / trailing areas. During a
 * parent intrinsic-min-height query with `width=0` (which Compose 1.7.x's
 * `AnimatedContent` / `Scaffold` / `SubcomposeLayout` cascade issues during
 * cold-launch of any screen that contains a `ListItem` inside a `LazyColumn`
 * inside a `Column` inside a `Scaffold` — i.e. nearly every Handy screen),
 * ListItem's internal `MeasurePolicy.measure` constructs a fresh
 * `Constraints(maxWidth = 0 - 83dp_internal_padding, ...)` without
 * `coerceAtLeast(0)`, immediately tripping
 *   `require(maxWidth >= minWidth && maxHeight >= minHeight)`.
 *
 * Exhausted hypotheses for defending the cascade upstream
 * (Sprint 30c-#1/2/3 — see AGENTS.md Sprint 30c closure entry for the
 * full diagnostic journey):
 *
 *  - **30c-#1**: Migrated destination screens to LazyColumn + dropped the
 *    outer `Column.fillMaxSize().verticalScroll(...)` wrapper. Closed the
 *    `"vertically scrollable component measured with infinity maximum
 *     height constraints"` runtime check; did NOT close `maxWidth(-83)`.
 *  - **30c-#2**: Restored `Box(Modifier.fillMaxWidth().weight(1f))` around
 *    the tab body in `SettingsTabsScreen`. (Believed at the time, then
 *    disproven, that `Modifier.weight(1f)` excludes the entry from
 *    `Column.minIntrinsicHeight` sum.) Did NOT close `maxWidth(-83)`.
 *  - **30c-#3**: Added `Modifier.requiredWidthIn(min = 100.dp)` directly on
 *    the `ListItem(...)` call. The required- min participates in the
 *    OUTER `ListItem` measure pass but does NOT propagate to ListItem's
 *    INNER SubcomposeLayout measure pass — ListItem's internal Padding
 *    formulas run with the original (maxWidth - paddings) math, producing
 *    `maxWidth(-83)`.
 *  - **30c-#4 (this)**: STOP fighting the cascade. **Eliminate the
 *    receiver.** Replace `ListItem` with a custom `Surface` + `Row` +
 *    `Column` layout that visually matches M3 ListItem but uses Compose's
 *    plain `RowMeasurePolicy` / `ColumnMeasurePolicy` — no SubcomposeLayout,
 *    no padding-subtract-from-zero, no intrinsic-cascade crash.
 *
 * Functional & visual parity preserved:
 *  - 48dp ripple when `onClick != null` (via `Modifier.clickable`).
 *  - `containerColor = surfaceContainerHigh` (via `Surface`).
 *  - Tone = `surfaceContainerHigh` so each row reads as a distinct
 *    surface against the parent `SettingsGroup` card.
 *  - Title typography = `bodyLarge` + `onSurface`.
 *  - Subtitle (when present) = `bodyMedium` + `onSurfaceVariant`.
 *  - `Modifier.fillMaxWidth()` retained; layout sizes 100% of available
 *    width regardless of intrinsic cascades.
 *
 * Backwards compat: `SettingsRow` / `SettingsRowDivider` in
 * [SettingsGroup.kt] still delegate to this file so existing call sites
 * (`AboutContent.kt`, `SettingsScreen.kt`) keep working without churn.
 *
 * On-device verify (A059, 192.168.1.36:38075, Android 16):
 *   `adb install -r` + `adb shell am start -n com.handy.app.debug/.MainActivity --ez skip_onboarding true`
 *   should cold-launch WITHOUT `FATAL EXCEPTION` and focus on `com.handy.app.MainActivity`.
 *   BEFORE-fix log for comparison: `handy-android/logs/sprint30c/full_crash_AFTER_Sprint30c-3.log`.
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
    val rowModifier = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

    Surface(
        modifier = rowModifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = Spacing.lg,
                vertical = if (subtitle != null) Spacing.md else Spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sprint 30c-#4 follow-up: leadingContent/Column/trailingContent
            // separated ONLY by the outer Row's `padding(horizontal = Spacing.lg)`
            // above (16dp total edge-to-content). NO inner Spacer to avoid
            // doubling the gap to 32dp which would not match M3 ListItem spec.
            leading?.invoke()
            // `.weight(1f)` ONLY — STRIPPED `.fillMaxWidth()` per code-reviewer.
            // Compose Layout contract: inside a `Row`, the Row's `weight(1f)`
            // child already receives the full remaining width allocated by
            // `RowMeasurePolicy`. Adding `fillMaxWidth()` AFTER weight can
            // produce inconsistent widths under Compose 1.7.x intrinsic-result
            // cascade (the exact scenario that triggered the original
            // `maxWidth(-83)` crash with M3 ListItem). Keeping it lean
            // avoids re-introducing the cascade trigger via Surface+Row.
            Column(
                modifier = Modifier
                    .weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * Convenience divider placed between [HandyListItem] rows. Uses MD3
 * [HorizontalDivider] tinted to `outlineVariant` so dark/light follow
 * automatically.
 *
 * NO change vs pre-Sprint 30c — dividers only use `Modifier.padding(start
 * = Spacing.lg)` and `HorizontalDivider`; no SubcomposeLayout cascade risk.
 */
@Composable
fun HandyListItemDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
