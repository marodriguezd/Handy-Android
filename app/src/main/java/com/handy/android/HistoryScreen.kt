package com.handy.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
        Text("History", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your completed transcriptions stay on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search history") },
            placeholder = { Text("Search by transcribed text") },
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = sourceFilter == null,
                onClick = { sourceFilter = null },
                label = { Text("All") },
            )
            HistorySourceFilters.forEach { source ->
                FilterChip(
                    selected = sourceFilter == source,
                    onClick = { sourceFilter = if (sourceFilter == source) null else source },
                    label = { Text(HistorySource.label(source)) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${entries.size} ${if (entries.size == 1) "entry" else "entries"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                enabled = entries.isNotEmpty(),
                onClick = { showClearDialog = true },
            ) { Text("Clear history") }
        }

        if (entries.isEmpty()) {
            Text(
                if (query.isBlank() && sourceFilter == null) {
                    "No transcriptions yet. Your next successful transcription will appear here."
                } else {
                    "No history entries match these filters."
                },
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
            title = { Text("Clear history?") },
            text = { Text("This permanently removes all saved transcriptions from this device.") },
            confirmButton = {
                Button(onClick = {
                    showClearDialog = false
                    scope.launch {
                        withContext(Dispatchers.IO) { repository.clear() }
                        refresh()
                    }
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
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
    Card {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(entry.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(HistorySource.label(entry.sourceType))
                    append(" · ")
                    append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.timestamp)))
                    if (entry.durationMs > 0) append(" · ${formatDuration(entry.durationMs)}")
                    entry.modelName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCopy) { Text("Copy") }
                TextButton(onClick = onShare) { Text("Share") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
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
    context.startActivity(Intent.createChooser(shareIntent, "Share transcription"))
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
