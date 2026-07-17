package com.handy.app.audio

import com.handy.app.settings.RetentionPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 25b Phase E — 4 JVM tests for the pure retention eviction
 * helper invoked by `RecordingRepository.evictByRetention`.
 *
 * Test surface: the off-by-one semantics on the boundary timestamp
 * matter because users see edges like "recording made yesterday,
 * eviction policy is `OneDay`". A bug here silently deletes
 * recordings the user expected to keep.
 */
class RetentionProviderTest {

    private val millisPerDay: Long = 24L * 60L * 60L * 1000L

    private fun entry(path: String, lastModifiedMillis: Long): AudioFileDescriptor =
        AudioFileDescriptor(path = path, lastModified = lastModifiedMillis, size = 1024L)

    @Test
    fun `Never period returns empty eviction list regardless of file ages`() {
        val entries = listOf(
            entry("/mem/a.wav", 0L),
            entry("/mem/b.wav", System.currentTimeMillis()),
        )
        assertEquals(emptyList<String>(), evictOlderThan(0L, RetentionPeriod.Never, entries))
    }

    @Test
    fun `OneDay removes files older than threshold but keeps boundary file`() {
        val now = 100L * millisPerDay
        val entries = listOf(
            entry("/mem/very-old.wav", now - 10L * millisPerDay),  // 10d old -> remove
            entry("/mem/two-day.wav", now - 2L * millisPerDay),    // 2d old -> remove
            entry("/mem/boundary.wav", now - 1L * millisPerDay),   // exactly 1d old -> keep (strict <)
            entry("/mem/young.wav", now - 1L),                     // 1ms old -> keep
        )
        val removed = evictOlderThan(now, RetentionPeriod.OneDay, entries)
        assertEquals(listOf("/mem/very-old.wav", "/mem/two-day.wav"), removed)
    }

    @Test
    fun `OneYear keeps everything under 365d and removes a 400d old file`() {
        val now = 500L * millisPerDay
        val entries = listOf(
            entry("/mem/recent.wav", now - 100L * millisPerDay),
            entry("/mem/year-old.wav", now - 365L * millisPerDay),  // boundary -> keep (strict <)
            entry("/mem/ancient.wav", now - 400L * millisPerDay),   // remove
        )
        val removed = evictOlderThan(now, RetentionPeriod.OneYear, entries)
        assertEquals(listOf("/mem/ancient.wav"), removed)
    }

    @Test
    fun `empty entries list returns empty eviction list regardless of period`() {
        RetentionPeriod.entries.forEach { period ->
            val removed = evictOlderThan(System.currentTimeMillis(), period, emptyList())
            assertTrue("$period with empty entries must return emptyList", removed.isEmpty())
        }
    }
}
