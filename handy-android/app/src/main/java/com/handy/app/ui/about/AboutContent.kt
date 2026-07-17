package com.handy.app.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.handy.app.BuildConfig
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.about.components.LocaleSelector
import com.handy.app.ui.about.components.ThemeSelector
import com.handy.app.ui.components.HandyInfoDialog
import com.handy.app.ui.components.SettingsGroup
import com.handy.app.ui.components.SettingsRow
import com.handy.app.ui.components.SettingsRowDivider
import com.handy.app.ui.components.Spacing
import com.handy.app.ui.theme.ThemeMode
import java.io.File

/**
 * About + Theme + Language screen. Replaces the old in-line
 * `AboutContent` that lived inside `SettingsScreen.kt`.
 *
 * Layout (3 sections, each wrapped in a `LazyColumn` `item`):
 *
 *  1. **APPEARANCE** — `ThemeSelector` + dynamic-color switch.
 *  2. **LANGUAGE**   — `LocaleSelector` + dynamic-locale hint.
 *  3. **ABOUT**      — App version, source link, data dirs, licenses.
 *
 * The locale selector writes `SettingsStore.appLanguage` and then
 * forwards the same tag to `AppCompatDelegate.setApplicationLocales`.
 * AndroidManifest declares `configChanges="locale|layoutDirection"`
 * on MainActivity so Compose can recompose strings without recreating
 * the Activity — recording state survives the switch automatically.
 *
 * **Sprint 28c-#2 migration**: root container migrated from
 * `Column(modifier.fillMaxWidth())` to `LazyColumn(modifier.fillMaxSize(),
 * contentPadding = PaddingValues(Spacing.lg))` for parity with
 * HistoryScreen / ModelCatalogScreen / PostProcessScreen. The outer
 * `Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()))`
 * wrapper that lived in `MainActivity.aboutContent` is gone — its only
 * job was to absorb the `Constraints.Infinity` that `AnimatedContent`'s
 * measure-pass feeds the destination body, but `LazyColumn` accepts
 * `Infinity` bounds (it measures only visible items, never intrinsic
 * content height) while `Column.verticalScroll` does not. Mirrors the
 * Sprint 28b-v15 DebugScreen fix and Sprint 28c-#1 PostProcess fix.
 */
@Composable
@Suppress("ModifierParameter")
fun AboutContent(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HandyApplication
    val themeMode by app.settingsStore.themeModeFlow.collectAsState()
    val dynamicColor by app.settingsStore.dynamicColorFlow.collectAsState()
    val appLanguage by app.settingsStore.appLanguageFlow.collectAsState()
    var showLicenseDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {

        // ── APPEARANCE ─────────────────────────────────────────────────
        item {
            SettingsGroup(title = stringResource(R.string.about_section_appearance)) {
                SettingsRow(
                    title = stringResource(R.string.about_theme_label),
                    subtitle = stringResource(R.string.about_theme_subtitle),
                )
                ThemeSelector(
                    selected = themeMode,
                    onSelect = { app.settingsStore.themeMode = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Spacing.lg,
                            vertical = Spacing.sm,
                        ),
                )
                SettingsRowDivider()
                SettingsRow(
                    title = stringResource(R.string.about_dynamic_color),
                    subtitle = stringResource(R.string.about_dynamic_color_desc),
                    trailing = {
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = { app.settingsStore.dynamicColor = it },
                        )
                    },
                )
            }
        }

        // ── LANGUAGE ───────────────────────────────────────────────────
        item {
            SettingsGroup(title = stringResource(R.string.about_section_language)) {
                SettingsRow(
                    title = stringResource(R.string.about_locale_label),
                    subtitle = stringResource(R.string.about_locale_subtitle),
                )
                LocaleSelector(
                    selected = appLanguage,
                    onSelect = { tag ->
                        // Sprint 23: persistence + AppCompat call live in parent
                        // so the composable stays side-effect-free.
                        app.settingsStore.appLanguage = tag
                        val localeList = if (tag.isNullOrEmpty()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(tag)
                        }
                        AppCompatDelegate.setApplicationLocales(localeList)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = Spacing.lg,
                            vertical = Spacing.sm,
                        ),
                )
            }
        }

        // ── ABOUT ──────────────────────────────────────────────────────
        item {
            SettingsGroup(title = stringResource(R.string.about_section_about)) {
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    trailing = { Text(BuildConfig.VERSION_NAME) },
                )
                SettingsRowDivider()

                // Source link
                SettingsRow(
                    title = stringResource(R.string.settings_github),
                    subtitle = stringResource(R.string.settings_github_url),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/handy-org/handy"))
                        context.startActivity(intent)
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                )
                SettingsRowDivider()

                // Data directory (taps copy the path to clipboard)
                SettingsRow(
                    title = stringResource(R.string.about_app_data_dir),
                    subtitle = app.filesDir.absolutePath,
                    onClick = { copyToClipboard(context, app.filesDir.absolutePath) },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                )
                SettingsRowDivider()

                // Log directory — typically `<filesDir>/logs` but we surface the parent
                // for parity with the PC sidebar.
                val logDir = File(app.filesDir, "logs")
                SettingsRow(
                    title = stringResource(R.string.about_log_dir),
                    subtitle = if (logDir.exists()) logDir.absolutePath
                    else stringResource(R.string.about_log_dir_missing),
                    onClick = {
                        if (logDir.exists()) copyToClipboard(context, logDir.absolutePath)
                    },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                )
                SettingsRowDivider()

                // Licenses dialog
                SettingsRow(
                    title = stringResource(R.string.settings_licenses),
                    onClick = { showLicenseDialog = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }

    if (showLicenseDialog) {
        HandyInfoDialog(
            title = stringResource(R.string.settings_licenses),
            message = stringResource(R.string.about_acknowledgments_text),
            onDismiss = { showLicenseDialog = false },
            okLabel = stringResource(R.string.dialog_ok),
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Handy", text))
}
