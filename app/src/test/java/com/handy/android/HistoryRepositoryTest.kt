package com.handy.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryRepositoryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun formatsSourceLabelsCorrectly() {
        assertEquals("Floating button", context.getString(HistorySource.labelRes(HistorySource.FLOATING_BUTTON)))
        assertEquals("Voice keyboard", context.getString(HistorySource.labelRes(HistorySource.INPUT_METHOD)))
        assertEquals("Voice recognition", context.getString(HistorySource.labelRes(HistorySource.VOICE_RECOGNITION)))
        assertEquals("Voice input", context.getString(HistorySource.labelRes(HistorySource.VOICE_INPUT)))
        assertEquals("Audio file", context.getString(HistorySource.labelRes(HistorySource.AUDIO_FILE)))
        assertEquals("Live subtitles", context.getString(HistorySource.labelRes(HistorySource.LIVE_SUBTITLE)))
        assertEquals("Other", context.getString(HistorySource.labelRes("custom_source")))
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
