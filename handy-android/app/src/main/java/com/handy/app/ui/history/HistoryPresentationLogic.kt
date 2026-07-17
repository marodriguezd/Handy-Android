package com.handy.app.ui.history

import com.handy.app.model.HistoryEntry

/**
 * Pure presentation helpers extracted for Sprint 24 (History con audio + retry).
 *
 * No Android, Compose, or coroutine references here — every function is
 * JVM-testable. Consumed by `HistoryScreen.kt` (progress markers,
 * retry-able gating) and `AudioPlayerBar.kt` (seek-bar fractional state).
 *
 * The full contract is pinned by `HistoryPresentationLogicTest`; do not add
 * untested branches to this file.
 */

/**
 * Zero-pad a non-negative Long to two ASCII digits. Callers must pass a
 * value in `0..99`; `pad2()` does **not** clamp wider numbers — passing
 * `100L` returns `"100"` (3 chars, no padding) by design, leaving the
 * caller to choose between two-digit compactness and three-digit
 * honesty. The only consumers today are minute/second components
 * (`% 3600` and `% 60`), so the contract is safe. If you ever need to
 * pad hours, do it explicitly with a wider format (`"$hours:" + …`).
 */
private fun Long.pad2(): String = toString().padStart(2, '0')

/**
 * Format a playback-millisecond timestamp as `MM:SS` (under an hour) or
 * `H:MM:SS` (at or above an hour). Negative input is coerced to `0L` so
 * the playback bar cannot display `-1:00` if upstream state carries an
 * invalid duration during fast composition cycles.
 *
 * **Why not `String.format("%02d:%02d", …)`**: that path would route
 * through `Locale.NumberFormat` and could emit locale-specific digits
 * (Arabic-Indic on Saudi Arabia builds, Bengali, etc.). Kotlin string
 * interpolation + `Long.toString()` always produce ASCII digits, which
 * matches every playback-time convention in the wild. If a future
 * maintainer "modernises" this with `String.format`, they'll regress
 * Arabic-speaking users — this KDoc is the guard rail.
 */
fun formatPlaybackTimeMs(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSec = safe / 1000L
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return when {
        hours > 0L -> "$hours:${mins.pad2()}:${secs.pad2()}"
        else -> "${mins.pad2()}:${secs.pad2()}"
    }
}

/**
 * Build a Slider value-range that survives `durationMs == 0` (which would
 * otherwise produce a `0f..-0f` range and crash M3's Slider when the user
 * drags). Always emits a non-negative range; negatives clamp to `0..0`.
 *
 * The companion `formatPlaybackTimeMs` matches the displayed ticks, so a
 * 0-duration playback shows `0f..0f` and `00:00 / 00:00` until the
 * duration lands from the audio pipeline. Callers should still coerce
 * the displayed `progressMs.toFloat()` into this range to defend against
 * upstream drift.
 *
 * Note: Float integer-exact range tops out around 16.7M; durations above
 * that will see sub-millisecond precision loss in the Slider drag's
 * resulting `(Float).toLong()` conversion. For ~20-minute recordings
 * (~1.2e9 ms) this still round-trips cleanly through Float → Long; for
 * longer-than-that an audio pipeline that loses precision is the real
 * fix, not this helper.
 */
fun safeSliderRange(durationMs: Long): ClosedFloatingPointRange<Float> {
    val duration = durationMs.coerceAtLeast(0L)
    return 0f..duration.toFloat()
}

/**
 * Whether the History entry has audio backing it that can be retranscribed.
 * Gates the "Retry" FilledTonalIconButton in `HistoryScreen.kt` — entries
 * transcribed without an audio file are not re-transcribable, so the
 * button stays hidden for them. Strings (text + post-processed text) are
 * still present, just not re-feedable to the engine.
 *
 * Cross-reference: see `HistoryCard` in `HistoryScreen.kt` — the
 * `if (entry.canRetry()) { FilledTonalIconButton(...) }` block is the
 * single source of truth for whether the Retry affordance appears for a
 * given entry. If `canRetry()` returns `false`, the button is hidden
 * entirely (no disabled state); this keeps the card compact for entries
 * that lack an audio file.
 */
fun HistoryEntry.canRetry(): Boolean =
    !audioPath.isNullOrBlank()
