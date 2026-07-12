package com.handy.app.ui.dictation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.R
import com.handy.app.viewmodel.EngineViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationScreen(
    engineViewModel: EngineViewModel,
    modifier: Modifier = Modifier,
) {
    val state by engineViewModel.state.collectAsState()
    val partialText by engineViewModel.partialText.collectAsState()
    val finalText by engineViewModel.finalText.collectAsState()
    val vadLevel by engineViewModel.vadLevel.collectAsState()

    val statusLog = remember { mutableStateListOf<String>() }

    LaunchedEffect(state) {
        val label = when (state) {
            EngineViewModel.STATE_IDLE -> "Idle"
            EngineViewModel.STATE_LOADING -> "Loading"
            EngineViewModel.STATE_LISTENING -> "Listening"
            EngineViewModel.STATE_TRANSCRIBING -> "Transcribing"
            EngineViewModel.STATE_ERROR -> "Error"
            EngineViewModel.STATE_CONFIRM -> "Confirm"
            else -> "Unknown"
        }
        statusLog.add(0, "[${statusLog.size}] $label")
    }

    val transcriptionText = if (finalText != null) {
        buildString {
            if (partialText.isNotEmpty()) {
                append(partialText)
                append("\n───\n")
            }
            append(finalText)
        }
    } else {
        partialText
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictation_title)) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            // ── Record Button ──────────────────────────────────
            RecordButton(
                state = state,
                onStart = { engineViewModel.startRecording() },
                onStop = { engineViewModel.stopRecording() },
                onRetry = {
                    engineViewModel.resetPartialText()
                    engineViewModel.startRecording()
                },
            )

            Spacer(Modifier.height(16.dp))

            // ── State Label ────────────────────────────────────
            Text(
                text = when (state) {
                    EngineViewModel.STATE_IDLE -> stringResource(R.string.state_idle)
                    EngineViewModel.STATE_LOADING -> stringResource(R.string.state_loading)
                    EngineViewModel.STATE_LISTENING -> stringResource(R.string.state_listening)
                    EngineViewModel.STATE_TRANSCRIBING -> stringResource(R.string.state_transcribing)
                    EngineViewModel.STATE_ERROR -> stringResource(R.string.state_error)
                    EngineViewModel.STATE_CONFIRM -> "Confirm"
                    else -> "Unknown"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(12.dp))

            // ── VAD Level Bar ──────────────────────────────────
            VadLevelBar(level = vadLevel)

            Spacer(Modifier.height(16.dp))

            // ── Transcription Text ──────────────────────────────
            OutlinedTextField(
                value = transcriptionText,
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(200.dp),
                readOnly = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.dictation_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(Modifier.height(12.dp))

            // ── Status Log ─────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(statusLog) { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RecordButton(
    state: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        EngineViewModel.STATE_IDLE,
        EngineViewModel.STATE_LOADING,
            -> {
            Surface(
                onClick = onStart,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                enabled = state != EngineViewModel.STATE_LOADING,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.dictation_start),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }

        EngineViewModel.STATE_LISTENING,
        EngineViewModel.STATE_TRANSCRIBING,
            -> {
            Surface(
                onClick = onStop,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.dictation_stop),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }

        EngineViewModel.STATE_ERROR -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.state_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.retry))
                }
            }
        }

        EngineViewModel.STATE_CONFIRM -> {
            Surface(
                onClick = onStart,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.dictation_start),
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun VadLevelBar(level: Float) {
    val targetColor = when {
        level < 0.3f -> Color(0xFF4CAF50)
        level < 0.6f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    val animatedColor by animateColorAsState(targetColor, label = "vadColor")

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { level.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = animatedColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "VAD: ${(level * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
