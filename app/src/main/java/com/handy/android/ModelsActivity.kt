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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
                val downloadableByCatalogId = remember(downloader.availableModels) {
                    downloader.availableModels.associateBy { it.catalogId }
                }
                var installed by remember { mutableStateOf(emptyList<InstalledModel>()) }
                var query by remember { mutableStateOf("") }
                var showAvailableOnly by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf("Choose a model for local transcription") }
                var downloadingId by remember { mutableStateOf<String?>(null) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) importModel(uri) { message ->
                        status = message
                        lifecycleScope.launch { installed = loadInstalledModels(downloader) }
                    }
                }

                fun matches(entry: ModelCatalogEntry): Boolean {
                    val normalizedQuery = query.trim().lowercase()
                    return normalizedQuery.isBlank() || listOf(
                        entry.name,
                        entry.parameters,
                        entry.architecture,
                        entry.description,
                    ).any { it.lowercase().contains(normalizedQuery) }
                }

                val filteredCatalog = ModelCatalog.models.filter { entry ->
                    matches(entry) && (!showAvailableOnly || entry.isAvailableOnAndroid)
                }
                val availableEntries = filteredCatalog.filter { it.isAvailableOnAndroid }
                val comingSoonEntries = filteredCatalog.filterNot { it.isAvailableOnAndroid }

                LaunchedEffect(Unit) { installed = loadInstalledModels(downloader) }

                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Spacer(Modifier.height(20.dp))
                        Text("Model store", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Browse Handy's mobile-sized catalog. Models are limited to ${formatParameterLimit(ModelCatalog.MAX_PARAMETERS)} parameters.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search models") },
                            placeholder = { Text("Name, language family, or architecture") },
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !showAvailableOnly,
                                onClick = { showAvailableOnly = false },
                                label = { Text("All models") },
                            )
                            FilterChip(
                                selected = showAvailableOnly,
                                onClick = { showAvailableOnly = true },
                                label = { Text("Available now") },
                            )
                        }
                    }
                    item {
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                            Text("Import GGML .bin")
                        }
                    }
                    if (installed.isNotEmpty()) {
                        item { Text("Installed models", style = MaterialTheme.typography.titleLarge) }
                        items(installed, key = { "installed-${it.file.name}" }) { installedModel ->
                            InstalledModelCard(
                                model = installedModel,
                                active = installedModel.validated &&
                                    SettingsManager.activeModelName(this@ModelsActivity) == installedModel.file.name,
                                onValidate = {
                                    status = "Validating ${installedModel.file.name} with Whisper…"
                                    lifecycleScope.launch {
                                        runCatching { downloader.validateAndActivate(installedModel.file) }
                                            .onSuccess { result ->
                                                status = "Active model: ${installedModel.file.name} (SHA-256 ${result.sha256.take(12)}…)"
                                                installed = loadInstalledModels(downloader)
                                            }
                                            .onFailure { error ->
                                                status = error.message ?: "Model validation failed"
                                            }
                                    }
                                },
                            )
                        }
                    }
                    item {
                        Text(
                            "Available on Android (${availableEntries.size})",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "These Whisper GGML models are supported by the current local Android engine.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(availableEntries, key = { "available-${it.id}" }) { entry ->
                        val model = downloadableByCatalogId[entry.id]
                        if (model != null) {
                            val installedAlready = installed.any { it.file.name == model.fileName }
                            StoreModelCard(
                                entry = entry,
                                installed = installedAlready,
                                downloading = downloadingId == entry.id,
                                downloadBlocked = downloadingId != null && downloadingId != entry.id,
                                onDownload = {
                                    if (downloadingId == null) {
                                        downloadingId = entry.id
                                        status = "Downloading ${entry.name}…"
                                        lifecycleScope.launch {
                                            runCatching { downloader.download(model) }
                                                .onSuccess { file ->
                                                    installed = loadInstalledModels(downloader)
                                                    status = "Downloaded and validated: ${file.name}. Validate and use it below to activate."
                                                }
                                                .onFailure { error ->
                                                    status = error.message ?: "Model download or validation failed"
                                                }
                                            downloadingId = null
                                        }
                                    }
                                },
                            )
                        }
                    }
                    if (comingSoonEntries.isNotEmpty()) {
                        item {
                            Text(
                                "Coming soon (${comingSoonEntries.size})",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "These models fit the mobile parameter limit, but their native Android backend is not available yet. They cannot be downloaded safely from this build.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(comingSoonEntries, key = { "soon-${it.id}" }) { entry ->
                            StoreModelCard(
                                entry = entry,
                                installed = false,
                                downloading = false,
                                downloadBlocked = false,
                                onDownload = null,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
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
                        SettingsManager.clearActiveModel(this@ModelsActivity)
                    }
                    moveIntoPlace(partial, target)
                    ModelValidator.writeDigestFile(target, validation)
                } finally {
                    partial.delete()
                }
                withContext(Dispatchers.Main) {
                    onFinished("Imported and validated: ${target.name}. Validate and use it below to activate.")
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

@androidx.compose.runtime.Composable
private fun InstalledModelCard(
    model: InstalledModel,
    active: Boolean,
    onValidate: () -> Unit,
) {
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(model.file.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        active -> "Active model"
                        model.validated -> "Validated and ready"
                        else -> "Not validated — cannot be used yet"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onValidate) {
                Text(if (active) "Revalidate" else "Validate and use")
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StoreModelCard(
    entry: ModelCatalogEntry,
    installed: Boolean,
    downloading: Boolean,
    downloadBlocked: Boolean,
    onDownload: (() -> Unit)?,
) {
    val available = entry.isAvailableOnAndroid
    Card(
        colors = if (available) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row {
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(entry.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(entry.parameters, style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModelMetadataChip("${formatModelSize(entry.downloadSizeBytes)} download")
                ModelMetadataChip("${entry.languageCount} ${if (entry.languageCount == 1) "language" else "languages"}")
                ModelMetadataChip(entry.architecture)
            }
            if (available) {
                Text(
                    if (installed) "Installed. Use the validation control above to activate it."
                    else "Download is hashed and Whisper-validated before activation.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onDownload?.invoke() },
                    enabled = !downloading && !downloadBlocked && !installed && onDownload != null,
                ) {
                    if (downloading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.width(18.dp).height(18.dp),
                        )
                    } else {
                        Text(if (installed) "Installed" else "Download")
                    }
                }
            } else {
                Text(
                    "Coming soon — Android support for ${entry.architecture} is not implemented yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {}, enabled = false) { Text("Not available yet") }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun ModelMetadataChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun formatParameterLimit(parameters: Long): String =
    "%.1fB".format(java.util.Locale.US, parameters / 1_000_000_000.0)
