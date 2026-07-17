package com.handy.app.ui.history

import com.handy.app.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure presentation helpers extracted in Sprint 24.
 *
 * Mirrors the testing style of `CatalogSorterTest` — JUnit4, plain data
 * class fixtures, no Android, no Compose. Verifies the contract pinned by
 * `HistoryPresentationLogic.kt` so future edits can't silently regress.
 */
class HistoryPresentationLogicTest {

    // ── formatPlaybackTimeMs ──────────────────────────────────────────

    @Test
    fun `formatPlaybackTimeMs zero returns 00 colon 00`() {
        assertEquals("00:00", formatPlaybackTimeMs(0L))
    }

    @Test
    fun `formatPlaybackTimeMs under one minute formats seconds only`() {
        // 45 000 ms = 45 seconds → "00:45"
        assertEquals("00:45", formatPlaybackTimeMs(45_000L))
    }

    @Test
    fun `formatPlaybackTimeMs over one minute formats minutes and seconds`() {
        // 125 000 ms = 2 min 5 sec → "02:05"
        assertEquals("02:05", formatPlaybackTimeMs(125_000L))
    }

    @Test
    fun `formatPlaybackTimeMs over one hour formats hours minutes and seconds`() {
        // 3 601 000 ms = 1 h 0 min 1 sec → "1:00:01"
        assertEquals("1:00:01", formatPlaybackTimeMs(3_601_000L))
    }

    @Test
    fun `formatPlaybackTimeMs negative input coerces to 00 colon 00`() {
        // Defensive — upstream state can briefly carry a negative during
        // re-composition. Never display a "-1:00" tick.
        assertEquals("00:00", formatPlaybackTimeMs(-50L))
    }

    // ── safeSliderRange ───────────────────────────────────────────────

    @Test
    fun `safeSliderRange zero duration returns 0 to 0`() {
        assertEquals(0f..0f, safeSliderRange(0L))
    }

    @Test
    fun `safeSliderRange negative duration clamps to 0 to 0`() {
        // Should never crash M3 Slider with a negative range.
        assertEquals(0f..0f, safeSliderRange(-100L))
    }

    @Test
    fun `safeSliderRange positive duration returns 0 to duration`() {
        assertEquals(0f..125_000f, safeSliderRange(125_000L))
    }

    @Test
    fun `safeSliderRange preserves Float roundtrip for realistic max duration`() {
        // 1_200_000_000 ms ≈ 20 minutes of 16 kHz mono audio — the realistic
        // upper bound before VAD trims silence. Tests two things:
        //   (1) the range start is exactly 0f, (2) `endInclusive.toLong()`
        //       round-trips back to the original millisecond count so a
        //       user's drag still maps to the right ms on the way out.
        // Float integer-exact range tops out around 16.7 M; above that,
        // sub-millisecond precision can silently drift. This guard makes
        // the magnitude boundary explicit so future regressions surface
        // in the test grid rather than as "playback snaps to wrong ms"
        // in production.
        val durationMs = 1_200_000_000L
        val r = safeSliderRange(durationMs)
        assertEquals(0f, r.start)
        assertEquals("Float → Long roundtrip should preserve ms",
            durationMs, r.endInclusive.toLong())
    }

    @Test
    fun `safeSliderRange clamps Long MAX_VALUE to a non-NaN Float range`() {
        // Defensive: prevent `0f..NaN` if the audio pipeline ever reports
        // an overflowed millisecond count.
        val r = safeSliderRange(Long.MAX_VALUE)
        assertEquals(0f, r.start)
        assertTrue("end should be > 0f for huge durations", r.endInclusive > 0f)
        assertFalse("end should not be NaN", r.endInclusive.isNaN())
    }

    // ── HistoryEntry.canRetry ─────────────────────────────────────────

    @Test
    fun `canRetry null audioPath returns false`() {
        val entry = entry(audioPath = null)
        assertFalse(entry.canRetry())
    }

    @Test
    fun `canRetry empty audioPath returns false`() {
        val entry = entry(audioPath = "")
        assertFalse(entry.canRetry())
    }

    @Test
    fun `canRetry whitespace-only audioPath returns false`() {
        // Defensive — a previous pipeline step may write "   " instead of
        // null after a failed recording.
        val entry = entry(audioPath = "   ")
        assertFalse(entry.canRetry())
    }

    @Test
    fun `canRetry valid audioPath returns true`() {
        val entry = entry(audioPath = "/sdcard/handy/audio/2026-07-17T14-23.wav")
        assertTrue(entry.canRetry())
    }

    // ── helpers ───────────────────────────────────────────────────────

    private fun entry(audioPath: String?): HistoryEntry = HistoryEntry(
        id = 1L,
        text = "transcribed text",
        postProcessedText = null,
        timestamp = 0L,
        isSaved = false,
        audioPath = audioPath,
    )
}
