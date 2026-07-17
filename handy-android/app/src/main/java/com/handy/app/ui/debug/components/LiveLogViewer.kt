package com.handy.app.ui.debug.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.SettingsGroup

/**
 * Sprint 28b — renders the last 50 lines of the in-memory Reactive
 * RingBuffer held by [HandyApplication.reactiveRingBuffer]. The
 * LazyColumn auto-scrolls to the newest line whenever a tick arrives
 * and the user has not scrolled away from the bottom. A long-tap
 * surface (`Modifier.heightIn`) keeps the surface clip-bounded so a
 * single 10 000-line log burst cannot fill the entire Debug screen.
 */
@Composable
fun LiveLogViewer(
    modifier: Modifier = Modifier,
    app: HandyApplication,
) {
    val lines by app.reactiveRingBuffer.tailFlow.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    SettingsGroup(
        title = stringResource(R.string.debug_log_liveviewer_label),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.debug_log_liveviewer_subtitle),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (lines.isEmpty()) {
            Text(
                text = stringResource(R.string.debug_log_liveviewer_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 360.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(8.dp),
            ) {
                LazyColumn(state = listState) {
                    items(lines) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
