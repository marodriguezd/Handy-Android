package com.handy.android

import android.media.AudioDeviceInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.android.ui.theme.HandyTheme

class TranscriptionSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HandyTheme { TranscriptionSettingsScreen { finish() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranscriptionSettingsScreen(onSaved: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var language by remember { mutableStateOf(SettingsManager.language(context)) }
    var translate by remember { mutableStateOf(SettingsManager.translate(context)) }
    var prompt by remember { mutableStateOf(SettingsManager.initialPrompt(context)) }
    var timeout by remember { mutableStateOf(SettingsManager.modelUnloadTimeoutMs(context).toString()) }
    var backend by remember { mutableStateOf(SettingsManager.gpuBackend(context)) }
    var buffer by remember { mutableStateOf(SettingsManager.extraRecordingBufferMs(context).toString()) }
    var mute by remember { mutableStateOf(SettingsManager.muteWhileRecording(context)) }
    var autoSubmit by remember { mutableStateOf(SettingsManager.autoSubmitEnabled(context)) }
    var removeFillers by remember { mutableStateOf(SettingsManager.removeFillerWordsEnabled(context)) }
    var trimSpace by remember { mutableStateOf(SettingsManager.trimTrailingSpaceEnabled(context)) }
    var boot by remember { mutableStateOf(SettingsManager.autoStartOnBoot(context)) }
    val devices = remember { AudioRecorder(context).availableInputDevices() }
    var selectedDevice by remember { mutableStateOf(SettingsManager.inputDeviceId(context)) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.engine_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onSaved) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.engine_settings_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.engine_decoding_title), style = MaterialTheme.typography.titleMedium)
                    SelectionField(stringResource(R.string.engine_language_label), language, listOf("auto", "es", "en", "fr", "de", "zh", "ja", "ru")) { language = it }
                    Toggle(stringResource(R.string.engine_translate), translate) { translate = it }
                    OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.engine_initial_prompt)) }, minLines = 2)
                    OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.engine_unload_label)) }, singleLine = true)
                    SelectionField(stringResource(R.string.engine_backend_label), backend, listOf("cpu", "vulkan")) { backend = it }
                    Text(stringResource(R.string.engine_backend_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(stringResource(R.string.engine_audio_title), style = MaterialTheme.typography.titleLarge)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(buffer, { buffer = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.engine_buffer_label)) }, singleLine = true)
                    Toggle(stringResource(R.string.engine_mute), mute) { mute = it }
                    DeviceSelection(devices, selectedDevice) { selectedDevice = it }
                }
            }

            Text(stringResource(R.string.engine_automation_title), style = MaterialTheme.typography.titleLarge)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Toggle(stringResource(R.string.engine_auto_submit), autoSubmit) { autoSubmit = it }
                    Toggle(stringResource(R.string.engine_remove_fillers), removeFillers) { removeFillers = it }
                    Toggle(stringResource(R.string.engine_trim_space), trimSpace) { trimSpace = it }
                    Toggle(stringResource(R.string.engine_boot), boot) { boot = it }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    SettingsManager.setLanguage(context, language)
                    SettingsManager.setTranslate(context, translate)
                    SettingsManager.setInitialPrompt(context, prompt)
                    SettingsManager.setModelUnloadTimeoutMs(context, timeout.toLongOrNull() ?: 0L)
                    SettingsManager.setGpuBackend(context, backend)
                    SettingsManager.setExtraRecordingBufferMs(context, buffer.toLongOrNull() ?: 300L)
                    SettingsManager.setMuteWhileRecording(context, mute)
                    SettingsManager.setInputDeviceId(context, selectedDevice)
                    SettingsManager.setAutoSubmitEnabled(context, autoSubmit)
                    SettingsManager.setRemoveFillerWordsEnabled(context, removeFillers)
                    SettingsManager.setTrimTrailingSpaceEnabled(context, trimSpace)
                    SettingsManager.setAutoStartOnBoot(context, boot)
                    onSaved()
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun SelectionField(label: String, value: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $value") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DeviceSelection(devices: List<AudioDeviceInfo>, selected: Int?, onSelected: (Int?) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val label = devices.firstOrNull { it.id == selected }?.let { deviceLabel(context, it) }
        ?: stringResource(R.string.engine_device_default)
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.engine_microphone_label, label))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(stringResource(R.string.engine_device_default)) },
                onClick = { onSelected(null); expanded = false },
            )
            devices.forEach { device ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(deviceLabel(context, device)) },
                    onClick = { onSelected(device.id); expanded = false },
                )
            }
        }
    }
}

private fun deviceLabel(context: android.content.Context, device: AudioDeviceInfo): String = when (device.type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->
        context.getString(R.string.engine_device_bluetooth, device.productName)
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES ->
        context.getString(R.string.engine_device_headset, device.productName)
    else -> context.getString(R.string.engine_device_internal, device.productName)
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
