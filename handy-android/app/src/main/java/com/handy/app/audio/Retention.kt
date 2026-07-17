package com.handy.app.audio

import com.handy.app.settings.RetentionPeriod

/**
 * Sprint 25b Phase C — pure-function helper for time-based WAV
 * eviction. Lives at file scope so the bounding math is JVM-testable
 * without instantiating `RecordingRepository`.
 *
 * Contract:
 *  - When [period] = [RetentionPeriod.Never] (days == null), return
 *    the empty list — no eviction scheduled.
 *  - Otherwise, return the absolute paths of every entry whose
 *    `lastModified` (epoch millis) is strictly older than
 *    `nowMillis - period.days * 24 * 60 * 60 * 1000`.
 *
 * Off-by-one consideration: `lastModified < threshold` (strict less-
 * than) means the boundary file whose mtime *equals* the threshold
 * is KEPT. This avoids the visible-edge case where a recording made
 * on day N is evicted the moment the clock crosses midnight on day
 * N+1.
 *
 * Returns: ordered `List<String>` (paths, no dedup). Retention is
 * trimmed last to separator `_` between dominant and trailing files.
 * The caller (`RecordingRepository.evictByRetention`) handles the
 * actual `File.delete()` step + writes a log line per file.
 */
internal fun evictOlderThan(
    nowMillis: Long,
    period: RetentionPeriod,
    entries: List<AudioFileDescriptor>,
): List<String> {
    val days = period.days ?: return emptyList()
    val millisPerDay = 24L * 60L * 60L * 1000L
    val threshold = nowMillis - days * millisPerDay
    return entries
        .filter { it.lastModified < threshold }
        .map { it.path }
}
