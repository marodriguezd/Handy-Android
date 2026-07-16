package com.handy.app.ui.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import com.handy.app.capability.CompatibilityStatus
import com.handy.app.capability.ModelCompatibility
import com.handy.app.model.ModelInfo
import com.handy.app.ui.components.HandyChipGroup
import com.handy.app.ui.components.HandySearchBar
import com.handy.app.ui.components.Spacing
import com.handy.app.ui.models.components.ActiveBadge
import com.handy.app.ui.models.components.CompatibilityBadgeChip
import com.handy.app.ui.models.components.DeviceCapabilityHeader
import com.handy.app.ui.models.components.HeavyModelWarningDialog
import com.handy.app.viewmodel.EngineViewModel
import com.handy.app.viewmodel.ModelsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModelCatalogScreen(viewModel: ModelsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Resolve @Composable string resources OUTSIDE any remember{} block.
    val allLanguagesLabel = stringResource(R.string.models_filter_all_languages)
    val searchPlaceholder = stringResource(R.string.models_search_placeholder)
    val recommendedLabel = stringResource(R.string.models_filter_recommended_only)
    val yourModelsLabel = stringResource(R.string.models_section_your_models)
    val availableModelsLabel = stringResource(R.string.models_section_available_models)
    val emptyCatalogLabel = stringResource(R.string.models_empty)
    val emptySearchLabel = stringResource(R.string.models_empty_search)

    val languageOptions = remember(uiState.models, allLanguagesLabel) {
        listOf<Pair<String?, String>>(null to allLanguagesLabel) +
            viewModel.availableLanguages().map { it to it }
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // ── Search bar (sticky beneath TopAppBar) ─────────────
            HandySearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::updateSearchQuery,
                placeholder = searchPlaceholder,
            )

            // ── Language filter chip row ──────────────────────────
            HandyChipGroup(
                options = languageOptions,
                selected = uiState.languageFilter,
                onSelect = viewModel::setLanguageFilter,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

            // ── Recommended-only toggle chip ─────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = uiState.onlyRecommended,
                    onClick = { viewModel.setOnlyRecommended(!uiState.onlyRecommended) },
                    label = { Text(recommendedLabel) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                )
            }

            // ── Content (loading / empty / list) ────────────────
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    uiState.models.isEmpty() && uiState.isLoading -> {
                        CircularProgressIndicator()
                    }
                    uiState.models.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.Info,
                            message = emptyCatalogLabel,
                        )
                    }
                    uiState.visibleModels.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Default.Info,
                            message = emptySearchLabel,
                        )
                    }
                    else -> CatalogList(
                        uiState = uiState,
                        yourModelsLabel = yourModelsLabel,
                        availableModelsLabel = availableModelsLabel,
                        onAttemptDownload = viewModel::attemptDownload,
                        onCancelDownload = viewModel::cancelDownload,
                        onDelete = viewModel::deleteModel,
                        onSetActive = viewModel::setActiveModel,
                        onToggleExperimental = viewModel::setShowExperimental,
                        onRefresh = viewModel::refreshCapability,
                    )
                }
            }
        }

        // Heavy / extreme confirmation dialog (Voxtral 24B, etc.)
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
private fun CatalogList(
    uiState: ModelsViewModel.UiState,
    yourModelsLabel: String,
    availableModelsLabel: String,
    onAttemptDownload: (ModelInfo) -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: (String) -> Unit,
    onSetActive: (String) -> Unit,
    onToggleExperimental: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        uiState.snapshot?.let { snap ->
            item(key = "capability_header") {
                DeviceCapabilityHeader(
                    snapshot = snap,
                    showExperimental = uiState.showExperimental,
                    onToggleExperimental = onToggleExperimental,
                    onRefresh = onRefresh,
                )
            }
        }

        val (yourModels, availableModels) = uiState.visibleModels
            .partition { it.first.isDownloaded || it.first.isActive }

        if (yourModels.isNotEmpty()) {
            item(key = "header_your") {
                SectionHeader(label = yourModelsLabel, count = yourModels.size)
            }
            items(yourModels, key = { it.first.id }) { (model, compatibility) ->
                ModelCard(
                    model = model,
                    compatibility = compatibility,
                    downloadState = uiState.downloads[model.id],
                    isActiveDownload = uiState.activeDownloadId == model.id,
                    onAttemptDownload = onAttemptDownload,
                    onCancel = onCancelDownload,
                    onDelete = onDelete,
                    onSetActive = onSetActive,
                )
            }
        }
        if (availableModels.isNotEmpty()) {
            item(key = "header_available") {
                SectionHeader(label = availableModelsLabel, count = availableModels.size)
            }
            items(availableModels, key = { it.first.id }) { (model, compatibility) ->
                ModelCard(
                    model = model,
                    compatibility = compatibility,
                    downloadState = uiState.downloads[model.id],
                    isActiveDownload = uiState.activeDownloadId == model.id,
                    onAttemptDownload = onAttemptDownload,
                    onCancel = onCancelDownload,
                    onDelete = onDelete,
                    onSetActive = onSetActive,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.extraSmall,
                )
                .padding(horizontal = Spacing.sm, vertical = 2.dp),
        )
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

    val cardShape = MaterialTheme.shapes.medium
    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(Spacing.md)) {
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
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (model.isActive) {
                        ActiveBadge()
                    }
                    compatibility.badges.forEach { b ->
                        CompatibilityBadgeChip(badge = b)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xs + 2.dp))

            // ── Row 2: Language chips + quant/size ──
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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
                    text = "${model.formattedSize()} · ${model.quant}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs, top = Spacing.xs),
                )
            }

            // ── Download progress ──
            if (downloadState != null && isActiveDownload) {
                Spacer(Modifier.height(Spacing.sm))
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
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = downloadState.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (isExceeding) {
                Spacer(Modifier.height(Spacing.xs + 2.dp))
                Text(
                    text = stringResource(R.string.model_unavailable_on_device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            // ── Row 3: Action buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (model.isDownloaded && !model.isActive) {
                    // 48dp touch target — M3 minimum for icon buttons.
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.models_delete),
                        )
                    }
                    Spacer(Modifier.width(Spacing.xs))
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

    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = Spacing.lg)

    when {
        model.isActive -> {
            // Active model — high emphasis using primary container.
            Card(
                modifier = cardModifier,
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) { cardContent() }
        }
        model.isDownloaded -> {
            // Downloaded but inactive — medium emphasis, secondary container.
            Card(
                modifier = cardModifier,
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) { cardContent() }
        }
        else -> {
            // Available for download — low emphasis, outlined.
            OutlinedCard(
                modifier = cardModifier,
                shape = cardShape,
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) { cardContent() }
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
        modifier = Modifier.padding(Spacing.xxl),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
