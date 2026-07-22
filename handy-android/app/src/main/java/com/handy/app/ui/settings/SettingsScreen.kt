@file:Suppress("BatteryLife")

package com.handy.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import rikka.shizuku.Shizuku
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.handy.app.BuildConfig
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandyDropdown
import com.handy.app.ui.components.HandyInfoDialog
import com.handy.app.ui.components.HandySegmentedButton
import com.handy.app.ui.components.SettingsGroup
import com.handy.app.ui.components.SettingsRow
import com.handy.app.ui.components.SettingsRowDivider
import com.handy.app.ui.components.Spacing
import com.handy.app.ui.components.StatusDot
import com.handy.app.ui.components.Status
import com.handy.app.ui.settings.components.AudioFeedbackToggle
import com.handy.app.ui.settings.components.MicrophoneSelector
import com.handy.app.ui.settings.components.ModelSettingsCard
import com.handy.app.ui.settings.components.SoundPicker
import com.handy.app.ui.settings.components.VolumeSlider
import com.handy.app.viewmodel.SettingsViewModel

/**
 * Sprint 19 rewrite of GeneralSettingsContent:
 *   - Wraps the UI in MD3 `SettingsGroup`s (Audio / Model / Shortcuts /
 *     Text injection / Battery) so the layout matches the PC sidebar
 *     grouping.
 *   - Uses `MicrophoneSelector`, `AudioFeedbackToggle`, `SoundPicker`,
 *     `VolumeSlider`, `ModelSettingsCard` for MD3-native rendering.
 *   - Reads/writes through the `SettingsStore` singleton (the existing
 *     pattern — the SettingsViewModel does not yet expose these as
 *     StateFlow, so direct SharedPreferences access keeps the diff
 *     small until Sprint 24 wires a proper VM).
 *
 * Sprint 30c-#1 migration: outer body migrated from
 *   `Column(modifier = modifier.fillMaxSize()) { ... }` to
 *   `LazyColumn(...)`.
 *
 * Settings redesign (2026-07-22):
 *   - Content is centered and capped at 640 dp to mirror the PC sidebar's
 *     `max-w-3xl` centered column.
 *   - Groups are rendered as compact outlined cards with reduced internal
 *     padding and optional header icons.
 *   - `LazyColumn` keeps its crash-safe role; the `SettingsTabsScreen`
 *     `Modifier.weight(1f)` wrapper is left untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsContent(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as HandyApplication
    val uiState by viewModel.uiState.collectAsState()
    var showShizukuDialog by remember { mutableStateOf(false) }

    if (showShizukuDialog) {
        HandyInfoDialog(
            title = stringResource(R.string.settings_shizuku_dialog_title),
            message = stringResource(R.string.settings_shizuku_dialog_message),
            onDismiss = { showShizukuDialog = false },
            okLabel = stringResource(R.string.dialog_ok),
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        val groupModifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()

        // ── Audio group ──
        item {
            SettingsGroup(
                title = stringResource(R.string.settings_audio_section_label),
                icon = Icons.Default.Mic,
                modifier = groupModifier,
            ) {
                MicrophoneSelector(
                    selected = app.settingsStore.selectedMicrophone,
                    onSelect = { app.settingsStore.selectedMicrophone = it },
                )
                SettingsRowDivider()
                AudioFeedbackToggle(
                    enabled = app.settingsStore.audioFeedbackEnabled,
                    onCheckedChange = { app.settingsStore.audioFeedbackEnabled = it },
                )
                SettingsRowDivider()
                SoundPicker(
                    selected = app.settingsStore.soundTheme,
                    onSelect = { app.settingsStore.soundTheme = it },
                    enabled = app.settingsStore.audioFeedbackEnabled,
                )
                SettingsRowDivider()
                VolumeSlider(
                    volume = app.settingsStore.audioFeedbackVolume,
                    onValueChange = { app.settingsStore.audioFeedbackVolume = it },
                    enabled = app.settingsStore.audioFeedbackEnabled,
                )
            }
        }

        // ── Custom Dictionary & Phonetic Corrector ──
        item {
            var showDictionarySheet by remember { mutableStateOf(false) }

            if (showDictionarySheet) {
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showDictionarySheet = false }
                ) {
                    DictionaryScreen()
                }
            }

            SettingsGroup(
                title = stringResource(R.string.settings_section_dictionary),
                icon = Icons.Default.Book,
                modifier = groupModifier,
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_custom_dictionary_title),
                    subtitle = stringResource(
                        R.string.settings_custom_dictionary_subtitle,
                        app.settingsStore.customWords.size,
                    ),
                    onClick = { showDictionarySheet = true },
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.content_desc_open_dictionary),
                        )
                    }
                )
                SettingsRowDivider()
                var fillerEnabled by remember { mutableStateOf(app.settingsStore.fillerWordsEnabled) }
                SettingsRow(
                    title = stringResource(R.string.settings_filler_removal_title),
                    subtitle = stringResource(R.string.settings_filler_removal_subtitle),
                    trailing = {
                        Switch(
                            checked = fillerEnabled,
                            onCheckedChange = { checked ->
                                fillerEnabled = checked
                                app.settingsStore.fillerWordsEnabled = checked
                            }
                        )
                    }
                )
            }
        }

        // ── Active model ──
        item {
            SettingsGroup(
                title = stringResource(R.string.settings_model_section_label),
                icon = Icons.Default.Memory,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                ModelSettingsCard(
                    currentModelId = app.settingsStore.selectedModel,
                    onUnload = {
                        com.handy.app.bridge.EngineBridge.nativeUnloadModel()
                    },
                )
            }
        }

        // ── Keyboard shortcuts / IME shortcuts ──
        item {
            SettingsGroup(
                title = stringResource(R.string.settings_shortcuts_label),
                icon = Icons.Default.Keyboard,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_switch_keyboard),
                    trailing = {
                        Button(
                            onClick = {
                                val imm = context.getSystemService(
                                    android.content.Context.INPUT_METHOD_SERVICE,
                                ) as InputMethodManager
                                imm.showInputMethodPicker()
                            },
                        ) {
                            Text(stringResource(R.string.settings_switch))
                        }
                    },
                )
            }
        }

        // ── Text injection (Shizuku) ──
        item {
            SettingsGroup(
                title = stringResource(R.string.settings_injection),
                icon = Icons.Default.Share,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                if (!BuildConfig.DEBUG) {
                    SettingsRow(
                        title = stringResource(R.string.settings_shizuku),
                        subtitle = stringResource(R.string.settings_shizuku_description),
                        leading = { StatusDot(status = shizukuStatus(app)) },
                        trailing = {
                            Switch(
                                checked = uiState.shizukuEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        try {
                                            val pingOk = Shizuku.pingBinder()
                                            val hasPerm = Shizuku.checkSelfPermission() == 0
                                            if (!pingOk || !hasPerm) {
                                                showShizukuDialog = true
                                            } else {
                                                viewModel.setShizukuEnabled(true)
                                            }
                                        } catch (_: Exception) {
                                            showShizukuDialog = true
                                        }
                                    } else {
                                        viewModel.setShizukuEnabled(false)
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }

        // ── Battery optimization ──
        item {
            SettingsGroup(
                title = stringResource(R.string.settings_battery),
                icon = Icons.Default.BatteryFull,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_battery_exemption),
                    subtitle = stringResource(R.string.settings_battery_exemption_description),
                    trailing = {
                        Switch(
                            checked = uiState.batteryOptimizationExempt,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val intent = Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    ).apply {
                                        setData(Uri.parse("package:${app.packageName}"))
                                    }
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try {
                                        app.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.w(
                                            "HandySettings",
                                            "Battery opt intent failed: $e",
                                        )
                                    }
                                }
                                viewModel.setBatteryOptimizationExempt(enabled)
                            },
                        )
                    },
                )
            }
        }
    }
}

private fun shizukuStatus(app: HandyApplication): Status = try {
    val pingOk = Shizuku.pingBinder()
    val hasPerm = Shizuku.checkSelfPermission() == 0
    val svcConnected = app.shizukuInjector.isAvailable()
    when {
        !pingOk || !hasPerm -> Status.Error
        svcConnected -> Status.Success
        else -> Status.Warning
    }
} catch (_: Exception) {
    Status.Error
}

// -----------------------------------------------------------------------
// Sprint 23: `AboutContent` was extracted to its own file at
// `com.handy.app.ui.about.AboutContent`. The AppNavigation route still calls
// it via the `aboutContent` lambda wired in `MainActivity`. The 3-section
// composition (APPEARANCE / LANGUAGE / ABOUT) lives next to its dedicated
// `ThemeSelector` and `LocaleSelector` siblings in `ui/about/components/`.
// -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Deprecated(
    message = "Sprint 26: Post-Process migrated to ui/postprocess/PostProcessScreen.kt. " +
        "Replaced with a top-level navigation-rail destination.",
    replaceWith = ReplaceWith(
        "PostProcessScreen()",
        "com.handy.app.ui.postprocess.PostProcessScreen",
    ),
)
fun PostProcessContent(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsGroup(title = stringResource(R.string.settings_postproc)) {
        SettingsRow(
            title = stringResource(R.string.settings_llm_endpoint),
            trailing = {
                OutlinedTextField(
                    value = uiState.postProcessEndpoint,
                    onValueChange = { viewModel.setPostProcessEndpoint(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://api.openai.com/v1/chat/completions") },
                )
            },
        )
        SettingsRowDivider()
        SettingsRow(
            title = stringResource(R.string.settings_api_key),
            trailing = {
                OutlinedTextField(
                    value = uiState.postProcessApiKey,
                    onValueChange = { viewModel.setPostProcessApiKey(it) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (uiState.isApiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { viewModel.toggleApiKeyVisibility() }) {
                            Icon(
                                imageVector = if (uiState.isApiKeyVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = stringResource(R.string.content_desc_toggle),
                            )
                        }
                    },
                )
            },
        )
    }
}

/**
 * Sprint 30c-#1 migration: outer body migrated from
 *   `Column(modifier = Modifier.fillMaxSize()) { ... }` to
 *   `LazyColumn(...)` for the same AnimatedContent-supplied Infinity
 *   reason documented on [GeneralSettingsContent]. LazyColumn owns the
 *   scroll surface here as well so MainActivity does NOT need to wrap
 *   the call with `Modifier.verticalScroll`.
 *
 * 5 SettingsGroups (Sprint 25b Advanced):
 *   - Sprint 25b Phase C `aplicacion` (experimental features toggle).
 *   - Sprint 25b Phase C `salida` (auto-send injection target).
 *   - Sprint 20 'transcripcion' (VAD + add-final-space).
 *   - Sprint 25b Phase C `history_retention` (history limit + retention
 *     period dropdowns).
 *   - Sprint 25b Phase C `experimental_features` (custom words +
 *     acceleration + LLM post-processing — gated by `experimentalEnabled`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsContent(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val app = context.applicationContext as HandyApplication

    // Sprint 30c-#1 state hoisting: these `collectAsState()` calls MUST live
    // outside the LazyColumn body block. The LazyColumn DSL receiver is
    // `LazyListScope`, NOT @Composable; calling `@Composable` functions
    // inside it produces the compile error
    //   "@Composable invocations can only happen from the context of a
    //    @Composable function"
    // at SettingsScreen.kt line 445/446/482/483 (the `item { ... }` blocks
    // that consume `customWordsRaw`/`accelerationBackend`/`historyLimit`/
    // `retentionPeriod`). Hoisting here resolves the constraint while
    // preserving LazyColumn's scroll surface. Same pattern as
    // GeneralSettingsContent, which already declared `uiState` +
    // `showShizukuDialog` at the function top.
    val historyLimit by app.settingsStore.historyLimitFlow.collectAsState()
    val retentionPeriod by app.settingsStore.retentionPeriodFlow.collectAsState()
    val customWordsRaw by app.settingsStore.customWordsRawFlow.collectAsState()
    val accelerationBackend by app.settingsStore.accelerationBackendFlow.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        item {
            SettingsGroup(
                title = stringResource(R.string.settings_section_application),
                icon = Icons.Default.Science,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_experimental_features),
                    subtitle = stringResource(R.string.settings_experimental_features_desc),
                    trailing = {
                        Switch(
                            checked = uiState.experimentalEnabled,
                            onCheckedChange = { viewModel.setExperimentalEnabled(it) },
                        )
                    },
                )
            }
        }
        item {
            SettingsGroup(
                title = stringResource(R.string.settings_section_output),
                icon = Icons.AutoMirrored.Filled.Send,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_auto_send),
                    trailing = {
                        var expanded by remember { mutableStateOf(false) }
                        val options = listOf(
                            "disabled" to stringResource(R.string.settings_auto_send_disabled),
                            "ime" to stringResource(R.string.settings_auto_send_ime),
                        )
                        val displayText = options.firstOrNull { it.first == uiState.autoSend }?.second
                            ?: stringResource(R.string.settings_auto_send_disabled)
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                        ) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
                            ) {
                                Text(displayText)
                            }
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                options.forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.setAutoSend(value)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
        item {
            SettingsGroup(
                title = stringResource(R.string.settings_section_transcription),
                icon = Icons.Default.Mic,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_vad),
                    subtitle = stringResource(R.string.settings_vad_desc),
                    trailing = {
                        Switch(
                            checked = uiState.vadEnabled,
                            onCheckedChange = { viewModel.setVadEnabled(it) },
                        )
                    },
                )
                SettingsRowDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_add_final_space),
                    subtitle = stringResource(R.string.settings_add_final_space_desc),
                    trailing = {
                        Switch(
                            checked = uiState.addFinalSpace,
                            onCheckedChange = { viewModel.setAddFinalSpace(it) },
                        )
                    },
                )
            }
        }

        // ── Sprint 25b Phase C: History & retention controls ─────────────
        // Compose-side direct read of [SettingsStore] flows (now hoisted
        // to the function top per the Sprint 30c-#1 LazyColumn migration).
        item {
            SettingsGroup(
                title = stringResource(R.string.advanced_section_history_retention),
                icon = Icons.Default.History,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                HandyDropdown(
                    label = stringResource(R.string.advanced_history_limit_title),
                    options = listOf(
                        com.handy.app.settings.HistoryLimit.Unlimited to stringResource(R.string.history_limit_unlimited),
                        com.handy.app.settings.HistoryLimit.Limited5 to stringResource(R.string.history_limit_5),
                        com.handy.app.settings.HistoryLimit.Limited10 to stringResource(R.string.history_limit_10),
                        com.handy.app.settings.HistoryLimit.Limited25 to stringResource(R.string.history_limit_25),
                        com.handy.app.settings.HistoryLimit.Limited50 to stringResource(R.string.history_limit_50),
                        com.handy.app.settings.HistoryLimit.Limited100 to stringResource(R.string.history_limit_100),
                        com.handy.app.settings.HistoryLimit.Limited250 to stringResource(R.string.history_limit_250),
                    ),
                    selected = historyLimit,
                    onSelect = { app.settingsStore.historyLimit = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
                SettingsRowDivider()
                HandyDropdown(
                    label = stringResource(R.string.advanced_retention_title),
                    options = listOf(
                        com.handy.app.settings.RetentionPeriod.Never to stringResource(R.string.retention_never),
                        com.handy.app.settings.RetentionPeriod.OneDay to stringResource(R.string.retention_one_day),
                        com.handy.app.settings.RetentionPeriod.OneWeek to stringResource(R.string.retention_one_week),
                        com.handy.app.settings.RetentionPeriod.OneMonth to stringResource(R.string.retention_one_month),
                        com.handy.app.settings.RetentionPeriod.OneYear to stringResource(R.string.retention_one_year),
                    ),
                    selected = retentionPeriod,
                    onSelect = { app.settingsStore.retentionPeriod = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }

        // ── Sprint 25b Phase C: Experimental features (gated by switch)─
        // (customWordsRaw + accelerationBackend state vars hoisted to
        // function top; see the Sprint 30c-#1 note above.)
        item {
            SettingsGroup(
                title = stringResource(R.string.advanced_section_experimental_features),
                icon = Icons.Default.Bolt,
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                SettingsRow(
                    title = stringResource(R.string.advanced_custom_words_title),
                    subtitle = stringResource(R.string.advanced_custom_words_subtitle),
                )
                OutlinedTextField(
                    value = customWordsRaw,
                    onValueChange = { app.settingsStore.customWordsRaw = it },
                    minLines = 3,
                    maxLines = 5,
                    enabled = uiState.experimentalEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    placeholder = { Text(stringResource(R.string.advanced_custom_words_placeholder)) },
                )
                SettingsRowDivider()
                SettingsRow(
                    title = stringResource(R.string.advanced_acceleration_title),
                    subtitle = stringResource(R.string.advanced_acceleration_subtitle),
                )
                HandySegmentedButton(
                    options = listOf(
                        com.handy.app.settings.AccelerationBackend.CPU to stringResource(R.string.acceleration_cpu),
                        com.handy.app.settings.AccelerationBackend.Vulkan to stringResource(R.string.acceleration_vulkan),
                        com.handy.app.settings.AccelerationBackend.NNAPI to stringResource(R.string.acceleration_nnapi),
                    ),
                    selected = accelerationBackend,
                    onSelect = { app.settingsStore.accelerationBackend = it },
                    enabled = uiState.experimentalEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
                SettingsRowDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_post_processing),
                    subtitle = stringResource(R.string.settings_post_processing_desc),
                    trailing = {
                        Switch(
                            checked = uiState.postProcessingEnabled,
                            onCheckedChange = { viewModel.setPostProcessingEnabled(it) },
                        )
                    },
                )
            }
        }
    }
}
