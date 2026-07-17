package com.handy.app.ui.history

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.model.HistoryEntry
import com.handy.app.ui.components.HandyConfirmDialog
import com.handy.app.ui.components.Spacing
import com.handy.app.ui.theme.YellowStar
import com.handy.app.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null &&
                    lastVisibleIndex >= uiState.entries.size - 5 &&
                    uiState.hasMore && !uiState.isLoading) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uiState.entries.isEmpty() && uiState.isLoading -> {
                    CircularProgressIndicator()
                }
                uiState.entries.isEmpty() && !uiState.isLoading -> {
                    EmptyState(
                        icon = Icons.Default.History,
                        message = stringResource(R.string.history_empty),
                    )
                }
                else -> {
                    LazyColumn(state = listState) {
                        items(uiState.entries, key = { it.id }) { entry ->
                            HistoryCard(
                                entry = entry,
                                isRetrying = uiState.retryingId == entry.id,
                                onDelete = { viewModel.deleteEntry(entry) },
                                onToggleSaved = { viewModel.toggleSaved(entry) },
                                onRetry = { viewModel.retry(entry) },
                                onCopy = { viewModel.copyText(entry) },
                            )
                        }
                        if (uiState.hasMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.lg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (uiState.isLoading) {
                                        CircularProgressIndicator(Modifier.size(Spacing.xxl))
                                    } else {
                                        OutlinedButton(onClick = { viewModel.loadMore() }) {
                                            Text(stringResource(R.string.history_load_more))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Refactored to MD3 in Sprint 24.
 *
 *  Touch targets: every action button is now `Spacing.huge` (48 dp) — the
 *  M3 accessibility minimum. The pre-Sprint-16 fix of 32 dp `@Modifier.size`
 *  was actively *violating* M3.
 *
 *  Semantic button colors per the user's Sprint 24 directive:
 *    - Save (star)         IconButton ghost         YellowStar tint when saved
 *    - Retry (canRetry)     FilledTonalIconButton   primaryContainer (CTA)
 *    - Copy                 FilledTonalIconButton   secondaryContainer
 *    - Delete               FilledTonalIconButton   errorContainer       (destructive)
 *
 *  While `isRetrying=true`, the Retry button's icon swaps for an inline
 *  `CircularProgressIndicator(24.dp)` — same pattern as AudioPlayerBar's
 *  buffering swap and the IME confirm-bar's RETRY spinner from Sprint 21.
 *
 *  Delete confirmation moved from inline `AlertDialog` to `HandyConfirmDialog`
 *  (Sprint 18 primitive) for tonal-surface consistency with the rest of the
 *  app.
 */
@Composable
fun HistoryCard(
    entry: HistoryEntry,
    isRetrying: Boolean,
    onDelete: () -> Unit,
    onToggleSaved: () -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = entry.formattedDate(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Save (star) — ghost IconButton; accent changes with
                // active state so the user sees saved vs unsaved at a
                // glance without a tonal container.
                IconButton(
                    onClick = onToggleSaved,
                    modifier = Modifier.size(Spacing.huge),
                ) {
                    Icon(
                        imageVector = if (entry.isSaved) Icons.Default.Star else Icons.Default.StarOutline,
                        contentDescription = stringResource(R.string.content_desc_save),
                        tint = if (entry.isSaved) YellowStar
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Retry — only when audioPath is recorded; primary-container
                // tonal makes it the strongest call-to-action.
                if (entry.canRetry()) {
                    FilledTonalIconButton(
                        onClick = onRetry,
                        modifier = Modifier.size(Spacing.huge),
                        enabled = !isRetrying,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        if (isRetrying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Spacing.xxl),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.history_retry),
                            )
                        }
                    }
                }

                // Copy — secondary container so the user sees the affordance
                // without making it the dominant CTA.
                FilledTonalIconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(Spacing.huge),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.content_desc_copy),
                    )
                }

                // Delete — destructive action earns the error-container
                // tonal so it never gets tapped by accident.
                FilledTonalIconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(Spacing.huge),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.content_desc_delete),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.text.length > 100) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) stringResource(R.string.history_show_less)
                               else stringResource(R.string.history_show_more),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            if (entry.postProcessedText != null && expanded) {
                Spacer(Modifier.height(Spacing.xs))
                HorizontalDivider()
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.history_post_processed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.postProcessedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDeleteDialog) {
        HandyConfirmDialog(
            title = stringResource(R.string.history_delete_confirm),
            message = stringResource(R.string.history_delete_confirm_message),
            confirmLabel = stringResource(R.string.dialog_delete),
            onConfirm = onDelete,
            onDismiss = { showDeleteDialog = false },
            dismissLabel = stringResource(R.string.dialog_cancel),
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, message: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
