package com.handy.app.ui.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import com.handy.app.capability.CompatibilityBadge
import com.handy.app.capability.CompatibilityStatus
import com.handy.app.capability.ModelCompatibility
import com.handy.app.model.ModelInfo
import com.handy.app.ui.models.components.ActiveBadge
import com.handy.app.ui.models.components.CompatibilityBadgeChip
import com.handy.app.ui.models.components.DeviceCapabilityHeader
import com.handy.app.ui.models.components.HeavyModelWarningDialog
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
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.models_refresh),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.models.isEmpty() && uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.models.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
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
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        uiState.snapshot?.let { snap ->
                            item(key = "capability_header") {
                                DeviceCapabilityHeader(
                                    snapshot = snap,
                                    showExperimental = uiState.showExperimental,
                                    onToggleExperimental = viewModel::setShowExperimental,
                                    onRefresh = viewModel::refreshCapability,
                                )
                            }
                        }

                        items(uiState.visibleModels, key = { it.first.id }) { (model, compatibility) ->
                            val downloadState = uiState.downloads[model.id]
                            val isActiveDownload = uiState.activeDownloadId == model.id
                            ModelCard(
                                model = model,
                                compatibility = compatibility,
                                downloadState = downloadState,
                                isActiveDownload = isActiveDownload,
                                onAttemptDownload = { viewModel.attemptDownload(it) },
                                onCancel = { viewModel.cancelDownload() },
                                onDelete = { viewModel.deleteModel(it) },
                                onSetActive = { viewModel.setActiveModel(it) },
                            )
                        }
                    }
                }
            }
        }

        // Heavy / extreme confirmation dialog (Vortextral 24B, etc.)
        uiState.showLargeModelDialogFor?.let { model ->
            uiState.snapshot?.let { snap ->
                HeavyModelWarningDialog(
                    model = model,
                    snapshot = snap,
                    onConfirm = { viewModel.confirmLargeModelDownload(model) },
                    onDismiss = { viewModel.cancelLargeModelDownload() },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelCard(
    model: ModelInfo,
    compatibility: ModelCompatibility,
    downloadState: EngineViewModel.DownloadProgressEvent?,
    isActiveDownload: Boolean,
    onAttemptDownload: (ModelInfo) -> Unit,
    onCancel: () -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isExceeding = compatibility.status == CompatibilityStatus.EXCEEDS ||
        compatibility.status == CompatibilityStatus.IMPOSSIBLE

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (model.isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (model.isActive) 2.dp else 1.dp,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ── Row 1: Icon + Title + Badges ──
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
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (model.isActive) {
                        ActiveBadge()
                    }
                    compatibility.badges.forEach { b ->
                        CompatibilityBadgeChip(badge = b)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Row 2: Language chips + quant/size ──
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val languages = model.language.split(",").map { it.trim() }
                    .filter { it.isNotEmpty() }
                for (lang in languages) {
                    SuggestionChip(
                        onClick = { },
                        label = {
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Text(
                    text = "${model.formattedSize()} \u00B7 ${model.quant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, top = 3.dp),
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
                        stringResource(R.string.download_progress_percent, (progress * 100).toInt())
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

            // ── Reason (when model exceeds device) ──
            if (isExceeding) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.model_unavailable_on_device),
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
                        OutlinedButton(onClick = { onAttemptDownload(model) }) {
                            Text(stringResource(R.string.models_retry))
                        }
                    }
                    isActiveDownload -> {
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.models_cancel))
                        }
                    }
                    !model.isDownloaded -> {
                        Button(
                            enabled = !compatibility.hidden,
                            onClick = { onAttemptDownload(model) },
                        ) {
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
