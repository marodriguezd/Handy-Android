package com.handy.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import rikka.shizuku.Shizuku
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.handy.app.BuildConfig
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsContent(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showShizukuDialog by remember { mutableStateOf(false) }

    if (showShizukuDialog) {
        AlertDialog(
            onDismissRequest = { showShizukuDialog = false },
            title = { Text(stringResource(R.string.settings_shizuku_dialog_title)) },
            text = {
                Text(stringResource(R.string.settings_shizuku_dialog_message))
            },
            confirmButton = {
                TextButton(onClick = { showShizukuDialog = false }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
        )
    }

    // ── Section: Audio ─────────────────────────────────────
    SectionHeader(stringResource(R.string.settings_audio))

    SettingsRow(
        title = stringResource(R.string.settings_idle_timeout),
        trailing = {
            var expanded by remember { mutableStateOf(false) }
            val timeoutDisplay = when (uiState.idleTimeout) {
                Int.MAX_VALUE -> stringResource(R.string.settings_idle_timeout_never)
                else -> "${uiState.idleTimeout}s"
            }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
            ) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true),
                ) {
                    Text(timeoutDisplay)
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    listOf(10, 30, 60, 120, Int.MAX_VALUE).forEach { seconds ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (seconds == Int.MAX_VALUE) stringResource(R.string.settings_idle_timeout_never) else "${seconds}s"
                                )
                            },
                            onClick = {
                                viewModel.setIdleTimeout(seconds)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
    )

    HorizontalDivider()

    // ── Section: Text Injection ─────────────────────────────
    SectionHeader(stringResource(R.string.settings_injection))

    val app = LocalContext.current.applicationContext as HandyApplication

    if (!BuildConfig.DEBUG) {
        SettingsRow(
            title = stringResource(R.string.settings_shizuku),
            subtitle = stringResource(R.string.settings_shizuku_description),
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val shizukuStatus = try {
                        val pingOk = Shizuku.pingBinder()
                        val hasPerm = Shizuku.checkSelfPermission() == 0
                        val svcConnected = app.shizukuInjector.isAvailable()
                        when {
                            !pingOk || !hasPerm -> androidx.compose.ui.graphics.Color.Red
                            svcConnected -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            else -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                        }
                    } catch (_: Exception) {
                        androidx.compose.ui.graphics.Color.Red
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(shizukuStatus)
                    )
                    Spacer(Modifier.width(8.dp))
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
                }
            },
        )
    }

    SettingsRow(
        title = stringResource(R.string.settings_switch_keyboard),
        trailing = {
            Button(
                onClick = {
                    val imm = app.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                },
            ) {
                Text(stringResource(R.string.settings_switch))
            }
        },
    )

    HorizontalDivider()

    // ── Section: Battery Optimization ──────────────────────
    SectionHeader(stringResource(R.string.settings_battery))

    SettingsRow(
        title = stringResource(R.string.settings_battery_exemption),
        subtitle = stringResource(R.string.settings_battery_exemption_description),
        trailing = {
            Switch(
                checked = uiState.batteryOptimizationExempt,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        ).apply {
                            setData(Uri.parse("package:${app.packageName}"))
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            app.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.w("HandySettings", "Battery opt intent failed: $e")
                        }
                    }
                    viewModel.setBatteryOptimizationExempt(enabled)
                },
            )
        },
    )

    HorizontalDivider()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostProcessContent(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // ── Section: Post-Processing ────────────────────────────
    SectionHeader(stringResource(R.string.settings_postproc))

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

    HorizontalDivider()
}

@Composable
fun AboutContent() {
    var showLicenseDialog by remember { mutableStateOf(false) }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(stringResource(R.string.settings_licenses)) },
            text = { Text(stringResource(R.string.settings_licenses_text)) },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
        )
    }

    // ── Section: About ─────────────────────────────────────
    SectionHeader(stringResource(R.string.settings_about))

    SettingsRow(
        title = stringResource(R.string.settings_version),
        trailing = { Text(BuildConfig.VERSION_NAME) },
    )

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

    HorizontalDivider()

    val context = LocalContext.current
    SettingsRow(
        title = stringResource(R.string.settings_github),
        subtitle = stringResource(R.string.settings_github_url),
        onClick = {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/handy-org/handy"),
            )
            context.startActivity(intent)
        },
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsContent(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // ── Section: APLICACIÓN ──
    SectionHeader(stringResource(R.string.settings_section_aplicacion))

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

    HorizontalDivider()

    // ── Section: SALIDA ──
    SectionHeader(stringResource(R.string.settings_section_salida))

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

    HorizontalDivider()

    // ── Section: TRANSCRIPCIÓN ──
    SectionHeader(stringResource(R.string.settings_section_transcripcion))

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

    HorizontalDivider()

    // ── Section: EXPERIMENTAL ──
    SectionHeader(stringResource(R.string.settings_section_experimental))

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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(text = title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = subtitle?.let { {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } },
        leadingContent = leadingContent,
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically) { trailing() } },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
