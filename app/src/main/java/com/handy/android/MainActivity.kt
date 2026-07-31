package com.handy.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HandyTheme { HandyScreen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HandyScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val downloader = remember { ModelDownloader(context) }
        val permissionPrefs = remember {
            context.getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
        }
        var permissions by remember { mutableStateOf(PermissionChecker.read(context)) }
        var serviceRunning by remember { mutableStateOf(false) }
        var downloading by remember { mutableStateOf(false) }
        var installed by remember { mutableStateOf(emptyList<String>()) }
        var modelStatus by remember { mutableStateOf<String?>(null) }
        var notificationRequestRejected by remember {
            mutableStateOf(permissionPrefs.getBoolean(NOTIFICATIONS_REJECTED, false))
        }
        var microphoneRequestRejected by remember {
            mutableStateOf(permissionPrefs.getBoolean(MICROPHONE_REJECTED, false))
        }
        var notificationWasRequested by remember {
            mutableStateOf(permissionPrefs.getBoolean(NOTIFICATIONS_REQUESTED, false))
        }
        var microphoneWasRequested by remember {
            mutableStateOf(permissionPrefs.getBoolean(MICROPHONE_REQUESTED, false))
        }

        fun refreshPermissions() {
            serviceRunning = FloatingButtonService.isRunning
            permissions = PermissionChecker.read(context)
            if (permissions.microphone) {
                microphoneRequestRejected = false
                permissionPrefs.edit().putBoolean(MICROPHONE_REJECTED, false).apply()
            } else if (microphoneWasRequested &&
                !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            ) {
                microphoneRequestRejected = true
                permissionPrefs.edit().putBoolean(MICROPHONE_REJECTED, true).apply()
            }
            if (permissions.notifications) {
                notificationRequestRejected = false
                permissionPrefs.edit().putBoolean(NOTIFICATIONS_REJECTED, false).apply()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                notificationWasRequested &&
                !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                notificationRequestRejected = true
                permissionPrefs.edit().putBoolean(NOTIFICATIONS_REJECTED, true).apply()
            }
        }

        val runtimePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            refreshPermissions()
        }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(Unit) {
            installed = withContext(Dispatchers.IO) {
                downloader.installedModels().map { model ->
                    if (TranscriptionEngine.isValidatedModel(model)) {
                        "✓ ${model.name}"
                    } else {
                        "⚠ ${model.name} (validation required)"
                    }
                }
            }
            refreshPermissions()
        }

        Scaffold(topBar = { TopAppBar(title = { Text("Handy") }) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Private voice input", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Transcribe speech locally and type it into the focused app.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                if (permissions.ready) "Handy is ready" else "Finish setup to start Handy",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                if (permissions.ready) {
                                    "All required access is enabled. The floating button can now be started."
                                } else {
                                    "Handy needs microphone, notifications, overlay, and accessibility access to work across apps."
                                },
                            )
                            Button(
                                enabled = !serviceRunning,
                                onClick = {
                                    when {
                                        !permissions.microphone || !permissions.notifications -> {
                                            if (microphoneRequestRejected || notificationRequestRejected) {
                                                openAppSettings()
                                            } else {
                                                microphoneWasRequested = !permissions.microphone
                                                notificationWasRequested =
                                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.notifications
                                                permissionPrefs.edit()
                                                    .putBoolean(MICROPHONE_REQUESTED, microphoneWasRequested)
                                                    .putBoolean(NOTIFICATIONS_REQUESTED, notificationWasRequested)
                                                    .apply()
                                                runtimePermissionLauncher.launch(PermissionChecker.runtimePermissions())
                                            }
                                        }
                                        !permissions.overlay -> openOverlaySettings()
                                        !permissions.accessibility -> openAccessibilitySettings()
                                        else -> {
                                            FloatingButtonService.start(context)
                                            serviceRunning = true
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    when {
                                        !permissions.microphone -> if (microphoneRequestRejected) "Open app settings" else "Allow microphone"
                                        !permissions.notifications -> if (notificationRequestRejected) "Open app settings" else "Allow notifications"
                                        !permissions.overlay -> "Allow overlay"
                                        !permissions.accessibility -> "Enable accessibility"
                                        serviceRunning -> "Handy running"
                                        else -> "Start Handy"
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Text("Permissions", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Complete each step below. Handy checks again automatically when you return from system settings.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    PermissionStep(
                        title = "Microphone",
                        description = "Records speech locally at 16 kHz mono.",
                        granted = permissions.microphone,
                        actionLabel = "Allow microphone",
                        onClick = {
                            if (microphoneRequestRejected) {
                                openAppSettings()
                            } else {
                                microphoneWasRequested = true
                                permissionPrefs.edit().putBoolean(MICROPHONE_REQUESTED, true).apply()
                                runtimePermissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        },
                    )
                }
                item {
                    PermissionStep(
                        title = "Notifications",
                        description = "Keeps the foreground recording service visible on Android 13 and newer.",
                        granted = permissions.notifications,
                        actionLabel = if (notificationRequestRejected) "Open notification settings" else "Allow notifications",
                        onClick = {
                            if (notificationRequestRejected) {
                                openAppSettings()
                            } else {
                                notificationWasRequested = true
                                permissionPrefs.edit().putBoolean(NOTIFICATIONS_REQUESTED, true).apply()
                                runtimePermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        },
                    )
                }
                item {
                    PermissionStep(
                        title = "Display over other apps",
                        description = "Shows the Handy recording button above the app you are using.",
                        granted = permissions.overlay,
                        actionLabel = "Open overlay settings",
                        onClick = { openOverlaySettings() },
                    )
                }
                item {
                    PermissionStep(
                        title = "Accessibility typing",
                        description = "Lets Handy insert a finished transcription into the focused text field. Handy does not read screen content beyond the focused input needed for typing.",
                        granted = permissions.accessibility,
                        actionLabel = "Open accessibility settings",
                        onClick = { openAccessibilitySettings() },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { startActivity(Intent(this@MainActivity, ModelsActivity::class.java)) }) { Text("Models") }
                        TextButton(onClick = { startActivity(Intent(this@MainActivity, CustomWordsActivity::class.java)) }) { Text("Custom words") }
                        TextButton(onClick = { startActivity(Intent(this@MainActivity, LiveSubtitleActivity::class.java)) }) { Text("Subtitles") }
                    }
                }
                item {
                    Text("Models", style = MaterialTheme.typography.titleMedium)
                    modelStatus?.let { status ->
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (installed.isEmpty()) Text("No local models yet. Download one to enable transcription.")
                    installed.forEach { model ->
                        Text(
                            model,
                            color = if (model.startsWith("✓")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
                items(downloader.availableModels) { model ->
                    Card {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                                Text(model.fileName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(enabled = !downloading, onClick = {
                                downloading = true
                                lifecycleScope.launch {
                                    runCatching { downloader.download(model) }
                                        .onSuccess { file ->
                                            modelStatus = "Downloaded and Whisper-validated: ${file.name}. Open Models to activate it."
                                        }
                                        .onFailure { error ->
                                            modelStatus = error.message ?: "Model download or validation failed"
                                        }
                                    installed = withContext(Dispatchers.IO) {
                                        downloader.installedModels().map { installedModel ->
                                            if (TranscriptionEngine.isValidatedModel(installedModel)) {
                                                "✓ ${installedModel.name}"
                                            } else {
                                                "⚠ ${installedModel.name} (validation required)"
                                            }
                                        }
                                    }
                                    downloading = false
                                }
                            }) {
                                if (downloading) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(18.dp).height(18.dp))
                                } else {
                                    Text("Download")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        )
    }

    companion object {
        private const val PERMISSION_PREFS = "permission_onboarding"
        private const val MICROPHONE_REQUESTED = "microphone_requested"
        private const val NOTIFICATIONS_REQUESTED = "notifications_requested"
        private const val MICROPHONE_REJECTED = "microphone_rejected"
        private const val NOTIFICATIONS_REJECTED = "notifications_rejected"
    }
}

@Composable
private fun PermissionStep(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = granted,
                    onClick = if (granted) ({}) else onClick,
                    label = { Text(if (granted) "Enabled" else "Needed") },
                )
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!granted) OutlinedButton(onClick = onClick) { Text(actionLabel) }
        }
    }
}

@Composable
private fun HandyTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
