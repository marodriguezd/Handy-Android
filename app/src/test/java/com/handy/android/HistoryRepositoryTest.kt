package com.handy.android

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRepositoryTest {
    @Test
    fun formatsSourceLabelsCorrectly() {
        assertEquals("Floating button", HistorySource.label(HistorySource.FLOATING_BUTTON))
        assertEquals("Voice keyboard", HistorySource.label(HistorySource.INPUT_METHOD))
        assertEquals("Voice recognition", HistorySource.label(HistorySource.VOICE_RECOGNITION))
        assertEquals("Voice input", HistorySource.label(HistorySource.VOICE_INPUT))
        assertEquals("Audio file", HistorySource.label(HistorySource.AUDIO_FILE))
        assertEquals("Live subtitles", HistorySource.label(HistorySource.LIVE_SUBTITLE))
        assertEquals("Other", HistorySource.label("custom_source"))
    }

    @Test
    fun historyEntryHoldsCorrectData() {
        val entry = HistoryEntry(
            id = 42L,
            text = "Test transcription",
            timestamp = 1_700_000_000_000L,
            durationMs = 3_500L,
            modelName = "small.bin",
            sourceType = HistorySource.FLOATING_BUTTON,
        )

        assertEquals(42L, entry.id)
        assertEquals("Test transcription", entry.text)
        assertEquals(1_700_000_000_000L, entry.timestamp)
        assertEquals(3_500L, entry.durationMs)
        assertEquals("small.bin", entry.modelName)
        assertEquals(HistorySource.FLOATING_BUTTON, entry.sourceType)
    }

    @Test
    fun databaseConstantsAreConsistent() {
        assertEquals("handy_history.db", HistoryDatabase.DATABASE_NAME)
        assertEquals("transcription_history", HistoryDatabase.TABLE_NAME)
        assertEquals(500, HistoryRepository.DEFAULT_MAX_ENTRIES)
    }
}

