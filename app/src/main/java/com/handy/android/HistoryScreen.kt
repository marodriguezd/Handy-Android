package com.handy.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { HistoryRepository(context) }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf(emptyList<HistoryEntry>()) }
    var showClearDialog by remember { mutableStateOf(false) }

    suspend fun refresh() {
        entries = withContext(Dispatchers.IO) {
            repository.listEntries(query, sourceFilter)
        }
    }

    DisposableEffect(repository) {
        onDispose {
            scope.coroutineContext.cancel()
            repository.close()
        }
    }
    LaunchedEffect(query, sourceFilter) { refresh() }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.history_subtitle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.history_search_label)) },
            placeholder = { Text(stringResource(R.string.history_search_placeholder)) },
        )
        val filterOptions = listOf<String?>(null) + HistorySourceFilters
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        ) {
            filterOptions.forEachIndexed { index, source ->
                SegmentedButton(
                    selected = sourceFilter == source,
                    onClick = { sourceFilter = if (sourceFilter == source) null else source },
                    shape = SegmentedButtonDefaults.itemShape(index, filterOptions.size),
                    icon = {},
                ) {
                    Text(stringResource(if (source == null) R.string.history_filter_all else HistorySource.labelRes(source)))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                pluralStringResource(R.plurals.history_count, entries.size, entries.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                enabled = entries.isNotEmpty(),
                onClick = { showClearDialog = true },
            ) { Text(stringResource(R.string.history_clear)) }
        }

        if (entries.isEmpty()) {
            Text(
                stringResource(
                    if (query.isBlank() && sourceFilter == null) R.string.history_empty else R.string.history_empty_filtered,
                ),
                modifier = Modifier.padding(vertical = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        onCopy = { copyEntry(context, entry.text) },
                        onShare = { shareEntry(context, entry.text) },
                        onDelete = {
                            scope.launch {
                                withContext(Dispatchers.IO) { repository.deleteEntry(entry.id) }
                                refresh()
                            }
                        },
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.history_clear_dialog_title)) },
            text = { Text(stringResource(R.string.history_clear_dialog_text)) },
            confirmButton = {
                Button(onClick = {
                    showClearDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.clear() }
                        refresh()
                    }
                }) { Text(stringResource(R.string.history_clear_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryEntryCard(
    entry: HistoryEntry,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var player by remember(entry.id) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(entry.id) { mutableStateOf(false) }
    DisposableEffect(entry.audioFilePath) {
        onDispose { player?.release() }
    }
    val sourceLabel = stringResource(HistorySource.labelRes(entry.sourceType))
    androidx.compose.material3.ElevatedCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(entry.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(sourceLabel)
                    append(" · ")
                    append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp)))
                    if (entry.durationMs > 0) append(" · ${formatDuration(entry.durationMs)}")
                    entry.modelName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) { Text(stringResource(R.string.history_copy)) }
                TextButton(onClick = onShare) { Text(stringResource(R.string.history_share)) }
                entry.audioFilePath?.let { path ->
                    TextButton(onClick = {
                        if (playing) {
                            player?.pause()
                            playing = false
                        } else {
                            player?.release()
                            player = runCatching {
                                MediaPlayer().apply {
                                    setDataSource(path)
                                    setOnCompletionListener { playing = false }
                                    prepare()
                                    start()
                                }
                            }.getOrNull()
                            playing = player != null
                        }
                    }) { Text(stringResource(if (playing) R.string.history_pause else R.string.history_play)) }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

private fun copyEntry(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Handy transcription", text))
}

private fun shareEntry(context: Context, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.history_share_chooser)))
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    return if (totalSeconds < 60) {
        "${totalSeconds}s"
    } else {
        "${totalSeconds / 60}m ${totalSeconds % 60}s"
    }
}

private val HistorySourceFilters = listOf(
    HistorySource.FLOATING_BUTTON,
    HistorySource.INPUT_METHOD,
    HistorySource.VOICE_RECOGNITION,
    HistorySource.VOICE_INPUT,
    HistorySource.AUDIO_FILE,
    HistorySource.LIVE_SUBTITLE,
)
