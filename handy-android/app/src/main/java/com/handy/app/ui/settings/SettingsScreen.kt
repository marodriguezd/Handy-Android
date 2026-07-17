@file:Suppress("BatteryLife")

package com.handy.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.handy.app.BuildConfig
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.HandyInfoDialog
import com.handy.app.ui.components.SettingsGroup
import com.handy.app.ui.components.SettingsRow
import com.handy.app.ui.components.SettingsRowDivider
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

    Column(modifier = modifier.fillMaxSize()) {
        // ── Audio group ──
        SettingsGroup(title = stringResource(R.string.settings_audio_section_label)) {
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

        // ── Active model ──
        SettingsGroup(title = stringResource(R.string.settings_model_section_label)) {
            ModelSettingsCard(
                currentModelId = app.settingsStore.selectedModel,
                onUnload = {
                    com.handy.app.bridge.EngineBridge.nativeUnloadModel()
                },
            )
        }

        // ── Keyboard shortcuts / IME shortcuts ──
        SettingsGroup(title = stringResource(R.string.settings_shortcuts_label)) {
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

        // ── Text injection (Shizuku) ──
        SettingsGroup(title = stringResource(R.string.settings_injection)) {
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

        // ── Battery optimization ──
        SettingsGroup(title = stringResource(R.string.settings_battery)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsContent(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsGroup(title = stringResource(R.string.settings_section_aplicacion)) {
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
        SettingsGroup(title = stringResource(R.string.settings_section_salida)) {
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
        SettingsGroup(title = stringResource(R.string.settings_section_transcripcion)) {
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
        SettingsGroup(title = stringResource(R.string.settings_section_experimental)) {
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
