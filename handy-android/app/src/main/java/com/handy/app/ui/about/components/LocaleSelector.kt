package com.handy.app.ui.about.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.HandyDropdown

/**
 * MD3-native Locale picker — wraps [HandyDropdown] with three entries:
 *
 *  * `null`   — System default (Handy follows `Locale.getDefault()`).
 *  * `"en"`   — English.
 *  * `"es"`   — Español.
 *
 * The component is intentionally **pure**: the parent
 * ([com.handy.app.ui.about.AboutContent]) is responsible for persisting
 * `appLanguage` to `SettingsStore` AND calling
 * `AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))`.
 *
 * Doing the AppCompat call in the parent keeps the locale change
 * reproducible and easy to test (the composable never has side-effects),
 * and mirrors the small handful of MD3 components that already use this
 * `selected + onSelect` contract.
 *
 * @param selected the persisted BCP-47 tag (`null` for System default).
 * @param onSelect invoked with the new tag (or `null` for System default).
 */
@Composable
@Suppress("ModifierParameter")
fun LocaleSelector(
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    HandyDropdown(
        label = stringResource(R.string.about_locale_label),
        options = listOf(
            null to stringResource(R.string.about_locale_system),
            "en" to stringResource(R.string.about_locale_en),
            "es" to stringResource(R.string.about_locale_es),
        ),
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        enabled = enabled,
    )
}
