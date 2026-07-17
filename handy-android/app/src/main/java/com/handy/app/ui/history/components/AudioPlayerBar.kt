package com.handy.app.ui.history.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.ui.components.Spacing
import com.handy.app.ui.history.formatPlaybackTimeMs
import com.handy.app.ui.history.safeSliderRange

/**
 * MD3 audio player bar — stateless.
 *
 * Caller owns playback state (`progressMs`, `durationMs`, `isBuffering`,
 * `isPlaying`) and supplies the action callbacks. The component itself
 * owns **no** playback pipeline: `HistoryViewModel` will be wired as
 * the playback source-of-truth in a follow-up sprint (after the JNI
 * `nativeRetryHistoryEntry` lands on the Rust side).
 *
 * Touch targets are 48 dp ([Spacing.huge]) for the play/pause button per
 * M3 accessibility minimum. While buffering, the icon swaps for an
 * inline `CircularProgressIndicator(24.dp)` — we deliberately do NOT
 * overlay the indicator on the Slider thumb, which would require
 * rewriting M3 Slider internals (brittle and version-locked). The
 * icon-swap approach matches the IME confirm-bar's own spinner-in-
 * FilledTonalIconButton pattern from Sprint 21.
 *
 * @param progressMs current playback position in ms. Coerced into
 *   [safeSliderRange] so upstream drift can't break the drag.
 * @param durationMs total track length in ms. Used both for the Slider
 *   `valueRange` and the trailing `MM:SS / HH:MM:SS` marker.
 * @param isBuffering when `true`, the play/pause button shows an
 *   inline spinner instead of an icon.
 * @param isPlaying when `true`, the button shows a Pause icon;
 *   otherwise a PlayArrow icon.
 * @param onSeek invoked with the new seek position in **ms** (Float
 *   already coerced and rounded) when the Slider drag stops.
 * @param onPlayPause invoked when the user taps the play/pause button.
 *   Toggling between buffering / playing / paused is the caller's job.
 * @param modifier standard Compose modifier slot.
 */
@Composable
fun AudioPlayerBar(
    progressMs: Long,
    durationMs: Long,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeRange = safeSliderRange(durationMs)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(Spacing.huge),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Spacing.xxl),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) R.string.history_audio_pause else R.string.history_audio_play,
                    ),
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Slider(
            value = progressMs.toFloat().coerceIn(safeRange),
            onValueChange = { newValue -> onSeek(newValue.toLong()) },
            valueRange = safeRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeTickColor = MaterialTheme.colorScheme.primary,
                inactiveTickColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = "${formatPlaybackTimeMs(progressMs)} / ${formatPlaybackTimeMs(durationMs)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
