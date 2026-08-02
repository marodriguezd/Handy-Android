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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.handy.android.ui.theme.HandyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Hoisted here (above HandyTheme) so the toggle applies the colour scheme live:
            // HandyTheme must receive an observable state, not a plain file read, or the
            // parent scope would never recompose when the child toggles it.
            var dynamicColorEnabled by remember {
                mutableStateOf(SettingsManager.dynamicColorEnabled(this@MainActivity))
            }
            HandyTheme(dynamicColor = dynamicColorEnabled) {
                HandyScreen(
                    dynamicColorEnabled = dynamicColorEnabled,
                    onDynamicColorChange = { enabled ->
                        dynamicColorEnabled = enabled
                        SettingsManager.setDynamicColorEnabled(this@MainActivity, enabled)
                    },
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    @Composable
    private fun HandyScreen(
        dynamicColorEnabled: Boolean,
        onDynamicColorChange: (Boolean) -> Unit,
    ) {
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

        val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
        val navigationSuiteType = when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Expanded, WindowWidthSizeClass.Medium -> NavigationSuiteType.NavigationRail
            else -> NavigationSuiteType.NavigationBar
        }

        NavigationSuiteScaffold(
            layoutType = navigationSuiteType,
            navigationSuiteItems = {
                MainTab.entries.forEach { tab ->
                    item(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                painterResource(tab.iconRes),
                                contentDescription = stringResource(tab.titleRes),
                            )
                        },
                        label = { Text(stringResource(tab.titleRes)) },
                    )
                }
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(title = { Text(stringResource(selectedTab.titleRes)) })
                when (selectedTab) {
                    MainTab.HISTORY -> HistoryScreen(Modifier.weight(1f))
                    MainTab.STORE -> ModelStoreTab(
                        installedCount = installed.size,
                        onOpenStore = { startActivity(Intent(this@MainActivity, ModelsActivity::class.java)) },
                        modifier = Modifier.weight(1f),
                    )
                    MainTab.TRANSCRIPTION -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.main_title), style = MaterialTheme.typography.headlineSmall)
                            Text(
                                stringResource(R.string.main_subtitle),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            ElevatedCard(
                                colors = CardDefaults.elevatedCardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    // Spec pair for a primaryContainer surface: onPrimaryContainer text.
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            ) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        stringResource(
                                            if (permissions.ready) R.string.main_ready_title else R.string.main_setup_title,
                                        ),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        stringResource(
                                            if (permissions.ready) R.string.main_ready_description else R.string.main_setup_description,
                                        ),
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
                                            stringResource(
                                                when {
                                                    !permissions.microphone -> if (microphoneRequestRejected) R.string.main_open_app_settings else R.string.main_allow_microphone
                                                    !permissions.notifications -> if (notificationRequestRejected) R.string.main_open_app_settings else R.string.main_allow_notifications
                                                    !permissions.overlay -> R.string.main_allow_overlay
                                                    !permissions.accessibility -> R.string.main_enable_accessibility
                                                    serviceRunning -> R.string.main_handy_running
                                                    else -> R.string.main_start_handy
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            Text(stringResource(R.string.main_permissions_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.main_permissions_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            PermissionStep(
                                title = stringResource(R.string.permission_microphone),
                                description = stringResource(R.string.permission_microphone_description),
                                granted = permissions.microphone,
                                actionLabel = stringResource(R.string.main_allow_microphone),
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
                                title = stringResource(R.string.permission_notifications),
                                description = stringResource(R.string.permission_notifications_description),
                                granted = permissions.notifications,
                                actionLabel = stringResource(
                                    if (notificationRequestRejected) R.string.permission_open_notification_settings else R.string.main_allow_notifications,
                                ),
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
                                title = stringResource(R.string.permission_overlay),
                                description = stringResource(R.string.permission_overlay_description),
                                granted = permissions.overlay,
                                actionLabel = stringResource(R.string.permission_open_overlay_settings),
                                onClick = { openOverlaySettings() },
                            )
                        }
                        item {
                            PermissionStep(
                                title = stringResource(R.string.permission_accessibility),
                                description = stringResource(R.string.permission_accessibility_description),
                                granted = permissions.accessibility,
                                actionLabel = stringResource(R.string.permission_open_accessibility_settings),
                                onClick = { openAccessibilitySettings() },
                            )
                        }
                        item {
                            Text(stringResource(R.string.main_feedback_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.main_feedback_description),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FeedbackToggle(
                                label = stringResource(R.string.main_sound_feedback),
                                checked = soundFeedbackEnabled,
                                onCheckedChange = {
                                    soundFeedbackEnabled = it
                                    SettingsManager.setSoundFeedbackEnabled(context, it)
                                },
                            )
                            FeedbackToggle(
                                label = stringResource(R.string.main_haptic_feedback),
                                checked = hapticFeedbackEnabled,
                                onCheckedChange = {
                                    hapticFeedbackEnabled = it
                                    SettingsManager.setHapticFeedbackEnabled(context, it)
                                },
                            )
                            FeedbackToggle(
                                label = stringResource(R.string.main_dynamic_color),
                                checked = dynamicColorEnabled,
                                onCheckedChange = onDynamicColorChange,
                            )
                        }
                        item {
                            Text(stringResource(R.string.main_settings_title), style = MaterialTheme.typography.titleMedium)
                        }
                        item {
                            Column {
                                SettingsRow(R.string.settings_models, Icons.Filled.Build) {
                                    startActivity(Intent(this@MainActivity, ModelsActivity::class.java))
                                }
                                HorizontalDivider()
                                SettingsRow(R.string.custom_words_title, Icons.Filled.Edit) {
                                    startActivity(Intent(this@MainActivity, CustomWordsActivity::class.java))
                                }
                                HorizontalDivider()
                                SettingsRow(R.string.postprocess_title, Icons.Filled.CheckCircle) {
                                    startActivity(Intent(this@MainActivity, PostProcessSettingsActivity::class.java))
                                }
                                HorizontalDivider()
                                SettingsRow(R.string.settings_ai_editing, Icons.Filled.Star) {
                                    startActivity(Intent(this@MainActivity, LlmSettingsActivity::class.java))
                                }
                                HorizontalDivider()
                                SettingsRow(R.string.settings_subtitles, Icons.Filled.PlayArrow) {
                                    startActivity(Intent(this@MainActivity, LiveSubtitleActivity::class.java))
                                }
                                HorizontalDivider()
                                SettingsRow(R.string.engine_settings_title, Icons.Filled.Settings) {
                                    startActivity(Intent(this@MainActivity, TranscriptionSettingsActivity::class.java))
                                }
                                HorizontalDivider()
                                SettingsRow(R.string.live_logs_title, Icons.AutoMirrored.Filled.List) {
                                    startActivity(Intent(this@MainActivity, LiveLogViewerActivity::class.java))
                                }
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }

    private enum class MainTab(@StringRes val titleRes: Int, @DrawableRes val iconRes: Int) {
        TRANSCRIPTION(R.string.nav_tab_transcription, R.drawable.ic_mic),
        STORE(R.string.nav_tab_store, R.drawable.ic_store),
        HISTORY(R.string.history_title, R.drawable.ic_history),
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
        Text(stringResource(R.string.model_store_title), style = MaterialTheme.typography.headlineSmall)
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                // Spec pair for a primaryContainer surface: onPrimaryContainer text.
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (installedCount == 0) {
                        stringResource(R.string.main_store_none)
                    } else {
                        pluralStringResource(R.plurals.main_store_installed, installedCount, installedCount)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    stringResource(R.string.main_store_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onOpenStore) { Text(stringResource(R.string.main_store_browse)) }
            }
        }
        Text(
            stringResource(R.string.main_store_footer),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsRow(
    @StringRes labelRes: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    )
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
    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilterChip(
                    selected = granted,
                    onClick = if (granted) ({}) else onClick,
                    label = { Text(stringResource(if (granted) R.string.permission_enabled else R.string.permission_needed)) },
                )
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!granted) OutlinedButton(onClick = onClick) { Text(actionLabel) }
        }
    }
}
