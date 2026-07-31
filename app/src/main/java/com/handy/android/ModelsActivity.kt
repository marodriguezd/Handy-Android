package com.handy.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class InstalledModel(
    val file: File,
    val validated: Boolean,
)

private suspend fun loadInstalledModels(downloader: ModelDownloader): List<InstalledModel> =
    withContext(Dispatchers.IO) {
        downloader.installedModels().map { file ->
            InstalledModel(file, TranscriptionEngine.isValidatedModel(file))
        }
    }

class ModelsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val downloader = remember { ModelDownloader(this@ModelsActivity) }
                var installed by remember { mutableStateOf(emptyList<InstalledModel>()) }
                var status by remember { mutableStateOf("Choose a model for local transcription") }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) importModel(uri) { message ->
                        status = message
                        lifecycleScope.launch { installed = loadInstalledModels(downloader) }
                    }
                }
                LaunchedEffect(Unit) { installed = loadInstalledModels(downloader) }
                LazyColumn(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("Models", style = MaterialTheme.typography.headlineSmall)
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                            Text("Import GGML .bin")
                        }
                    }
                    items(installed, key = { it.file.name }) { installedModel ->
                        val model = installedModel.file
                        val active = installedModel.validated &&
                            SettingsManager.activeModelName(this@ModelsActivity) == model.name
                        Card {
                            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        when {
                                            active -> "Active model"
                                            installedModel.validated -> "Validated and ready"
                                            else -> "Not validated — cannot be used yet"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(onClick = {
                                    status = "Validating ${model.name} with Whisper…"
                                    lifecycleScope.launch {
                                        runCatching { downloader.validateAndActivate(model) }
                                            .onSuccess { result ->
                                                status = "Active model: ${model.name} (SHA-256 ${result.sha256.take(12)}…)"
                                                installed = loadInstalledModels(downloader)
                                            }
                                            .onFailure { error ->
                                                status = error.message ?: "Model validation failed"
                                            }
                                    }
                                }) {
                                    Text(if (active) "Revalidate" else "Validate and use")
                                }
                            }
                        }
                    }
                    items(downloader.availableModels, key = { it.id }) { model ->
                        Card {
                            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(model.fileName)
                                    Text(
                                        if (model.expectedSha256 == null) {
                                            "Download is hashed and Whisper-checked locally; activation is explicit."
                                        } else {
                                            "Download is checked against its published SHA-256."
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Button(onClick = {
                                    status = "Downloading ${model.displayName}…"
                                    lifecycleScope.launch {
                                        runCatching { downloader.download(model) }
                                            .onSuccess { file ->
                                                installed = loadInstalledModels(downloader)
                                                status = "Downloaded and validated: ${file.name}. Select ‘Validate and use’ to activate."
                                            }
                                            .onFailure { status = it.message ?: "Download failed" }
                                    }
                                }) { Text("Download") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun importModel(uri: Uri, onFinished: (String) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val name = requireNotNull(uri.lastPathSegment).substringAfterLast('/').substringAfterLast(':')
                    .ifBlank { "imported-model.bin" }
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                require(name.endsWith(".bin", true)) { "Choose a GGML .bin Whisper model" }
                val target = File(File(filesDir, "models").apply { mkdirs() }, name)
                val partial = File(target.path + ".part")
                partial.delete()
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Unable to open selected file" }
                        partial.outputStream().use { output -> input.copyTo(output) }
                    }
                    require(partial.isFile && partial.length() > 0L) { "Selected model is empty" }
                    val validation = ModelValidator.validate(partial)
                    if (SettingsManager.activeModelName(this@ModelsActivity) == target.name) {
                        // Fail closed while replacing an active model; it must be explicitly reactivated.
                        SettingsManager.clearActiveModel(this@ModelsActivity)
                    }
                    moveIntoPlace(partial, target)
                    ModelValidator.writeDigestFile(target, validation)
                } finally {
                    partial.delete()
                }
                withContext(Dispatchers.Main) {
                    onFinished("Imported and validated: ${target.name}. Select ‘Validate and use’ to activate.")
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) { onFinished(error.message ?: "Import failed") }
            }
        }
    }

    private fun moveIntoPlace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.FileAlreadyExistsException) {
            target.delete()
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        check(target.isFile && target.length() > 0L) { "Could not finalize imported model" }
    }
}
