package com.handy.app.ui.about.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.HandySegmentedButton
import com.handy.app.ui.theme.ThemeMode

/**
 * MD3-native Theme selector — 3-way [HandySegmentedButton] wrapping the
 * Handy PC sidebar's `ThemeSelector.tsx`. Mirrors the `ThemeMode` enum
 * persisted in `SettingsStore.themeModeFlow` and writes through the
 * [onSelect] callback.  Selection is reactive (Compose `State<ThemeMode>`
 * in the parent); we deliberately do NOT call `SettingsStore.themeMode`
 * directly here so the Composable remains pure and unit-testable.
 */
@Composable
@Suppress("ModifierParameter")
fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    HandySegmentedButton(
        options = listOf(
            ThemeMode.System to stringResource(R.string.about_theme_system),
            ThemeMode.Light to stringResource(R.string.about_theme_light),
            ThemeMode.Dark to stringResource(R.string.about_theme_dark),
        ),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
    )
}
