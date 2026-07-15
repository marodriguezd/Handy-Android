package com.handy.app.ui.models

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.model.ModelInfo
import com.handy.app.viewmodel.EngineViewModel
import com.handy.app.viewmodel.ModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCatalogScreen(viewModel: ModelsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadModels()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.models_title)) },
                actions = {
                    IconButton(onClick = { viewModel.loadModels() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.models_refresh))
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            uiState.models.isEmpty() && uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.models.isEmpty() && !uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Default.Info,
                        message = stringResource(R.string.models_empty),
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.models, key = { it.id }) { model ->
                        val downloadState = uiState.downloads[model.id]
                        val isActiveDownload = uiState.activeDownloadId == model.id
                        ModelCard(
                            model = model,
                            downloadState = downloadState,
                            isActiveDownload = isActiveDownload,
                            onDownload = { viewModel.downloadModel(it) },
                            onCancel = { viewModel.cancelDownload() },
                            onDelete = { viewModel.deleteModel(it) },
                            onSetActive = { viewModel.setActiveModel(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: ModelInfo,
    downloadState: EngineViewModel.DownloadProgressEvent?,
    isActiveDownload: Boolean,
    onDownload: (String) -> Unit,
    onCancel: () -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSizeWarning by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (model.isActive) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    )
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            // ── Row 1: Icon + Title ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (model.isDownloaded) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Add
                    },
                    contentDescription = null,
                    tint = if (model.isDownloaded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (model.isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = stringResource(R.string.models_active),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Row 2: Language chip + Size info ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = model.language,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${model.formattedSize()} \u00B7 ${model.quant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Download progress ──
            if (downloadState != null && isActiveDownload) {
                Spacer(Modifier.height(8.dp))
                val progress = downloadState.progress
                if (progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = if (progress >= 0f) {
                        "Downloading ${(progress * 100).toInt()}%"
                    } else {
                        stringResource(R.string.models_downloading)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (downloadState?.isComplete == true && downloadState.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = downloadState.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Row 3: Action buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model.isDownloaded && !model.isActive) {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.models_delete),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                when {
                    model.isActive -> {
                        Button(onClick = {}, enabled = false) {
                            Text(stringResource(R.string.models_active))
                        }
                    }
                    downloadState?.error != null -> {
                        OutlinedButton(onClick = { onDownload(model.id) }) {
                            Text(stringResource(R.string.models_retry))
                        }
                    }
                    isActiveDownload -> {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.models_cancel))
                        }
                    }
                    !model.isDownloaded -> {
                        Button(onClick = {
                            val maxMem = Runtime.getRuntime().maxMemory()
                            if (model.sizeBytes > maxMem * 0.5) {
                                showSizeWarning = true
                            } else {
                                onDownload(model.id)
                            }
                        }) {
                            Text(stringResource(R.string.models_download))
                        }
                    }
                    !model.isActive -> {
                        OutlinedButton(onClick = { onSetActive(model.id) }) {
                            Text(stringResource(R.string.models_use))
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.models_confirm_delete)) },
            text = { Text(stringResource(R.string.models_confirm_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(model.id)
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.models_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    if (showSizeWarning) {
        AlertDialog(
            onDismissRequest = { showSizeWarning = false },
            title = { Text(stringResource(R.string.models_size_warning)) },
            text = { Text(stringResource(R.string.models_size_warning_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showSizeWarning = false
                    onDownload(model.id)
                }) {
                    Text(stringResource(R.string.models_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSizeWarning = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, message: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
