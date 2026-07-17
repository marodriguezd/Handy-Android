package com.handy.app.settings

/**
 * Sprint 25b Phase C — how long to keep WAV files on disk before
 * `RecordingRepository.evictByRetention` deletes them.
 *
 * `days == null` means NEVER — keep recordings indefinitely (the cap
 * is then the on-disk size budget only; see
 * `RecordingRepository.DEFAULT_MAX_STORAGE_BYTES`).
 *
 * `days == N` means a recording older than N days relative to
 * `nowMillis` is evicted on the next recording start. The eviction
 * is *lazy* — we do not run a background sweeper; users see the
 * effect on their next dictation session or when the size cap kicks
 * in first.
 */
enum class RetentionPeriod(val days: Long?) {
    Never(null),
    OneDay(1L),
    OneWeek(7L),
    OneMonth(30L),
    OneYear(365L),
}
