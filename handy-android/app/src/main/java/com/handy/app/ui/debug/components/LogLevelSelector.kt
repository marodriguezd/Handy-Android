package com.handy.app.ui.debug.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandyDropdown
import com.handy.app.ui.components.SettingsRow

/**
 * Sprint 28b — pick the LiveLogViewer verbosity. Backed by
 * [com.handy.app.SettingsStore.logLevelFlow]. The five levels map to
 * android.util.Log priorities:
 *
 *   VERBOSE (2) > DEBUG (3) > INFO (4) > WARN (5) > ERROR (6)
 *
 * Reads from [HandyApplication.settingsStore.logLevelFlow] are
 * reactive via `collectAsState()`, so changing the level from the
 * dropdown immediately re-emits to LiveLogViewer's filter predicate.
 */
@Composable
fun LogLevelSelector(
    modifier: Modifier = Modifier,
    app: HandyApplication,
    enabled: Boolean = true,
) {
    val selected by app.settingsStore.logLevelFlow.collectAsState()
    val label = stringResource(R.string.debug_log_loglevel_label)
    val options: List<Pair<String, String>> = listOf(
        "VERBOSE" to stringResource(R.string.debug_loglevel_verbose),
        "DEBUG" to stringResource(R.string.debug_loglevel_debug),
        "INFO" to stringResource(R.string.debug_loglevel_info),
        "WARN" to stringResource(R.string.debug_loglevel_warn),
        "ERROR" to stringResource(R.string.debug_loglevel_error),
    )
    SettingsRow(
        title = label,
        subtitle = stringResource(R.string.debug_loglevel_subtitle),
        trailing = {
            HandyDropdown(
                label = label,
                options = options,
                selected = selected,
                onSelect = { app.settingsStore.logLevel = it },
                // Sprint 30c-#6: do NOT fill the parent Row here — this is
                // the trailing slot of a `SettingsRow`/`HandyListItem`. A
                // greedy width starves the title/subtitle column and causes
                // the same huge-card bug seen on Settings.
                modifier = Modifier,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}
