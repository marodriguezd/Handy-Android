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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showShizukuDialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
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
                            modifier = Modifier.menuAnchor(),
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

            SettingsRow(
                title = stringResource(R.string.settings_shizuku),
                subtitle = stringResource(R.string.settings_shizuku_description),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shizukuStatus = try {
                            val pingOk = moe.shizuku.api.Shizuku.pingBinder()
                            val hasPerm = moe.shizuku.api.Shizuku.checkSelfPermission() == 0
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
                                        val pingOk = moe.shizuku.api.Shizuku.pingBinder()
                                        val hasPerm = moe.shizuku.api.Shizuku.checkSelfPermission() == 0
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
                                    contentDescription = "Toggle",
                                )
                            }
                        },
                    )
                },
            )

            HorizontalDivider()

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
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}
