package com.handy.android

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.handy.android.ui.theme.HandyTheme
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
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HandyTheme {
                val downloader = remember { ModelDownloader(this@ModelsActivity) }
                val downloadableByCatalogId = remember(downloader.availableModels) {
                    downloader.availableModels.associateBy { it.catalogId }
                }
                var installed by remember { mutableStateOf(emptyList<InstalledModel>()) }
                var query by remember { mutableStateOf("") }
                var showAvailableOnly by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf(getString(R.string.model_status_initial)) }
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

                val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(stringResource(R.string.model_store_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { innerPadding ->
                    LazyColumn(
                        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                stringResource(R.string.model_store_subtitle, formatParameterLimit(ModelCatalog.MAX_PARAMETERS)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text(stringResource(R.string.model_search_label)) },
                                placeholder = { Text(stringResource(R.string.model_search_placeholder)) },
                            )
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = !showAvailableOnly,
                                    onClick = { showAvailableOnly = false },
                                    label = { Text(stringResource(R.string.model_filter_all)) },
                                )
                                FilterChip(
                                    selected = showAvailableOnly,
                                    onClick = { showAvailableOnly = true },
                                    label = { Text(stringResource(R.string.model_filter_available)) },
                                )
                            }
                        }
                        item {
                            Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { picker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                                Text(stringResource(R.string.model_import))
                            }
                        }
                        if (installed.isNotEmpty()) {
                            item { Text(stringResource(R.string.model_installed_title), style = MaterialTheme.typography.titleLarge) }
                            items(installed, key = { "installed-${it.file.name}" }) { installedModel ->
                                InstalledModelCard(
                                    model = installedModel,
                                    active = installedModel.validated &&
                                        SettingsManager.activeModelName(this@ModelsActivity) == installedModel.file.name,
                                    onValidate = {
                                        status = getString(R.string.model_validating, installedModel.file.name)
                                        lifecycleScope.launch {
                                            runCatching { downloader.validateAndActivate(installedModel.file) }
                                                .onSuccess { result ->
                                                    status = getString(
                                                        R.string.model_active_status,
                                                        installedModel.file.name,
                                                        result.sha256.take(12),
                                                    )
                                                    installed = loadInstalledModels(downloader)
                                                }
                                                .onFailure { error ->
                                                    status = error.message ?: getString(R.string.model_invalid)
                                                }
                                        }
                                    },
                                )
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.model_available_title, availableEntries.size),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(R.string.model_available_description),
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
                                            status = getString(R.string.model_downloading, entry.name)
                                            lifecycleScope.launch {
                                                runCatching { downloader.download(model) }
                                                    .onSuccess { file ->
                                                        installed = loadInstalledModels(downloader)
                                                        status = getString(R.string.model_downloaded_status, file.name)
                                                    }
                                                    .onFailure { error ->
                                                        status = error.message ?: getString(R.string.model_download_or_validation_failed)
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
                                    stringResource(R.string.model_coming_soon_title, comingSoonEntries.size),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text(
                                    stringResource(R.string.model_coming_soon_description),
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
    }

    private fun importModel(uri: Uri, onFinished: (String) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val name = requireNotNull(uri.lastPathSegment).substringAfterLast('/').substringAfterLast(':')
                    .ifBlank { "imported-model.bin" }
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                require(name.endsWith(".bin", true)) { getString(R.string.model_import_require_bin) }
                val target = File(File(filesDir, "models").apply { mkdirs() }, name)
                val partial = File(target.path + ".part")
                partial.delete()
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { getString(R.string.model_import_open_failed) }
                        partial.outputStream().use { output -> input.copyTo(output) }
                    }
                    require(partial.isFile && partial.length() > 0L) { getString(R.string.model_import_empty) }
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
                    onFinished(getString(R.string.model_imported_status, target.name))
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) { onFinished(error.message ?: getString(R.string.model_import_failed)) }
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
        check(target.isFile && target.length() > 0L) { getString(R.string.model_import_finalize_failed) }
    }
}

@Composable
private fun InstalledModelCard(
    model: InstalledModel,
    active: Boolean,
    onValidate: () -> Unit,
) {
    ElevatedCard {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(model.file.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        when {
                            active -> R.string.model_active
                            model.validated -> R.string.model_validated_ready
                            else -> R.string.model_not_validated
                        },
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onValidate) {
                Text(stringResource(if (active) R.string.model_revalidate else R.string.model_validate_use))
            }
        }
    }
}

@Composable
private fun StoreModelCard(
    entry: ModelCatalogEntry,
    installed: Boolean,
    downloading: Boolean,
    downloadBlocked: Boolean,
    onDownload: (() -> Unit)?,
) {
    val available = entry.isAvailableOnAndroid
    ElevatedCard(
        colors = if (available) {
            CardDefaults.elevatedCardColors()
        } else {
            // Disabled/coming-soon container: surfaceContainerHigh is the MD3 role for
            // a higher-emphasis container (surfaceVariant is deprecated for containers).
            CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
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
                ModelMetadataChip(stringResource(R.string.model_download_size, formatModelSize(entry.downloadSizeBytes)))
                ModelMetadataChip(
                    pluralStringResource(R.plurals.model_language_count, entry.languageCount, entry.languageCount),
                )
                ModelMetadataChip(entry.architecture)
            }
            if (available) {
                Text(
                    stringResource(
                        if (installed) R.string.model_installed_hint else R.string.model_download_hint,
                    ),
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
                        Text(stringResource(if (installed) R.string.model_installed else R.string.model_download))
                    }
                }
            } else {
                Text(
                    stringResource(R.string.model_coming_soon_entry, entry.architecture),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.model_not_available)) }
            }
        }
    }
}

@Composable
private fun ModelMetadataChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun formatParameterLimit(parameters: Long): String =
    "%.1fB".format(java.util.Locale.US, parameters / 1_000_000_000.0)
