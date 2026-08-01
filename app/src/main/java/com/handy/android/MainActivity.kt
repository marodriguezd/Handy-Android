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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
        var installed by remember { mutableStateOf(emptyList<String>()) }
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
        var selectedTab by remember { mutableStateOf(MainTab.TRANSCRIPTION) }
        var soundFeedbackEnabled by remember { mutableStateOf(SettingsManager.soundFeedbackEnabled(context)) }
        var hapticFeedbackEnabled by remember { mutableStateOf(SettingsManager.hapticFeedbackEnabled(context)) }

        fun refreshInstalledModels() {
            lifecycleScope.launch {
                installed = withContext(Dispatchers.IO) {
                    downloader.installedModels().map { model ->
                        if (TranscriptionEngine.isValidatedModel(model)) {
                            "✓ ${model.name}"
                        } else {
                            "⚠ ${model.name} (validation required)"
                        }
                    }
                }
            }
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
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshPermissions()
                    refreshInstalledModels()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(Unit) {
            refreshInstalledModels()
            refreshPermissions()
        }

        Scaffold(
            topBar = { TopAppBar(title = { Text(selectedTab.title) }) },
            bottomBar = {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Text(tab.shortLabel) },
                            label = { Text(tab.title) },
                        )
                    }
                }
            },
        ) { padding ->
            if (selectedTab == MainTab.HISTORY) {
                HistoryScreen(Modifier.padding(padding))
                return@Scaffold
            }
            if (selectedTab == MainTab.STORE) {
                ModelStoreTab(
                    installedCount = installed.size,
                    onOpenStore = { startActivity(Intent(this@MainActivity, ModelsActivity::class.java)) },
                    modifier = Modifier.padding(padding),
                )
                return@Scaffold
            }
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
                    Text("Feedback", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose whether Handy signals recording and transcription events with sound or vibration.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FeedbackToggle(
                        label = "Sound feedback",
                        checked = soundFeedbackEnabled,
                        onCheckedChange = {
                            soundFeedbackEnabled = it
                            SettingsManager.setSoundFeedbackEnabled(context, it)
                        },
                    )
                    FeedbackToggle(
                        label = "Haptic feedback",
                        checked = hapticFeedbackEnabled,
                        onCheckedChange = {
                            hapticFeedbackEnabled = it
                            SettingsManager.setHapticFeedbackEnabled(context, it)
                        },
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { startActivity(Intent(this@MainActivity, ModelsActivity::class.java)) }) { Text("Models") }
                            TextButton(onClick = { startActivity(Intent(this@MainActivity, CustomWordsActivity::class.java)) }) { Text("Custom words") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { startActivity(Intent(this@MainActivity, PostProcessSettingsActivity::class.java)) }) { Text("Post-processing") }
                            TextButton(onClick = { startActivity(Intent(this@MainActivity, LiveSubtitleActivity::class.java)) }) { Text("Subtitles") }
                        }
                    }
                }
                item {
                    Text("Models", style = MaterialTheme.typography.titleMedium)
                    if (installed.isEmpty()) {
                        Text("No local models yet. Open the model store to choose one.")
                    } else {
                        Text(
                            "${installed.size} local model${if (installed.size == 1) "" else "s"}. Open Models to review or activate them.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                }
            }
        }
    }

    private enum class MainTab(val title: String, val shortLabel: String) {
        TRANSCRIPTION("Transcription", "Mic"),
        STORE("Store", "Models"),
        HISTORY("History", "Past"),
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
private fun ModelStoreTab(
    installedCount: Int,
    onOpenStore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Model store", style = MaterialTheme.typography.headlineSmall)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (installedCount == 0) "No models installed" else "$installedCount model${if (installedCount == 1) " is" else "s are"} installed",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "Download or import a Whisper GGML model to transcribe speech locally. Models are validated before they can be activated.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenStore) { Text("Browse all models") }
            }
        }
        Text(
            "The full catalog includes supported downloads, installed-model validation, and coming-soon entries.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeedbackToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
