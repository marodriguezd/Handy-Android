package com.handy.android

import android.media.AudioDeviceInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class TranscriptionSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { TranscriptionSettingsScreen { finish() } } }
    }
}

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
    var warmMic by remember { mutableStateOf(SettingsManager.alwaysOnMicrophoneEnabled(context)) }
    var autoSubmit by remember { mutableStateOf(SettingsManager.autoSubmitEnabled(context)) }
    var removeFillers by remember { mutableStateOf(SettingsManager.removeFillerWordsEnabled(context)) }
    var trimSpace by remember { mutableStateOf(SettingsManager.trimTrailingSpaceEnabled(context)) }
    var boot by remember { mutableStateOf(SettingsManager.autoStartOnBoot(context)) }
    val devices = remember { AudioRecorder(context).availableInputDevices() }
    var selectedDevice by remember { mutableStateOf(SettingsManager.inputDeviceId(context)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Transcription engine", style = MaterialTheme.typography.headlineSmall)
        Text("Whisper language, decoding context, memory and system behavior.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionField("Language", language, listOf("auto", "es", "en", "fr", "de", "zh", "ja", "ru")) { language = it }
        Toggle("Translate to English", translate) { translate = it }
        OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text("Initial prompt") }, minLines = 2)
        OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Unload model after idle (ms; 0 = keep loaded)") }, singleLine = true)
        SelectionField("Backend", backend, listOf("cpu", "vulkan")) { backend = it }
        Text("Vulkan is used when the packaged native build exposes the backend; CPU remains the safe default.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("Audio", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(buffer, { buffer = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Extra recording buffer (ms)") }, singleLine = true)
        Toggle("Duck other audio while recording", mute) { mute = it }
        Toggle("Keep microphone warm between captures", warmMic) { warmMic = it }
        DeviceSelection(devices, selectedDevice) { selectedDevice = it }

        Text("Automation", style = MaterialTheme.typography.titleLarge)
        Toggle("Submit with Enter after insertion", autoSubmit) { autoSubmit = it }
        Toggle("Remove filler words (eh, um, este)", removeFillers) { removeFillers = it }
        Toggle("Trim trailing spaces", trimSpace) { trimSpace = it }
        Toggle("Start floating button after boot", boot) { boot = it }
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
                SettingsManager.setAlwaysOnMicrophoneEnabled(context, warmMic)
                SettingsManager.setInputDeviceId(context, selectedDevice)
                SettingsManager.setAutoSubmitEnabled(context, autoSubmit)
                SettingsManager.setRemoveFillerWordsEnabled(context, removeFillers)
                SettingsManager.setTrimTrailingSpaceEnabled(context, trimSpace)
                SettingsManager.setAutoStartOnBoot(context, boot)
                onSaved()
            },
        ) { Text("Save") }
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
    var expanded by remember { mutableStateOf(false) }
    val label = devices.firstOrNull { it.id == selected }?.let(::deviceLabel) ?: "System default"
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Microphone: $label") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("System default") }, onClick = { onSelected(null); expanded = false })
            devices.forEach { device ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(deviceLabel(device)) }, onClick = { onSelected(device.id); expanded = false })
            }
        }
    }
}

private fun deviceLabel(device: AudioDeviceInfo): String = when (device.type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (${device.productName})"
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Headset (${device.productName})"
    else -> "Internal (${device.productName})"
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
