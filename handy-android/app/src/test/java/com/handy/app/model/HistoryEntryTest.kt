package com.handy.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [HistoryEntry].
 *
 * Coverage:
 * - JSON round-trip parsing with all fields
 * - Null handling for optional fields
 * - formattedDate basic shape (relative date keywords)
 */
class HistoryEntryTest {

    @Test
    fun `fromJsonArray parses full entry with all optional fields`() {
        val json = """
            [
                {
                    "id": 1,
                    "text": "hello world",
                    "post_processed_text": "Hello world.",
                    "timestamp": 1690000000000,
                    "is_saved": true,
                    "audio_path": "/sdcard/audio.wav",
                    "target_package": "com.example.app"
                }
            ]
        """.trimIndent()

        val entries = HistoryEntry.fromJsonArray(json)
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals(1L, entry.id)
        assertEquals("hello world", entry.text)
        assertEquals("Hello world.", entry.postProcessedText)
        assertEquals(1690000000000L, entry.timestamp)
        assertTrue(entry.isSaved)
        assertEquals("/sdcard/audio.wav", entry.audioPath)
        assertEquals("com.example.app", entry.targetPackage)
    }

    @Test
    fun `fromJsonArray handles null optional fields`() {
        val json = """
            [
                {
                    "id": 2,
                    "text": "no optional fields",
                    "post_processed_text": null,
                    "timestamp": 1690000000001,
                    "is_saved": false,
                    "audio_path": null
                }
            ]
        """.trimIndent()

        val entries = HistoryEntry.fromJsonArray(json)
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertNull(entry.postProcessedText)
        assertNull(entry.audioPath)
        assertNull(entry.targetPackage)
    }

    @Test
    fun `fromJsonArray returns empty list for blank input`() {
        assertTrue(HistoryEntry.fromJsonArray("").isEmpty())
        assertTrue(HistoryEntry.fromJsonArray("   ").isEmpty())
    }

    @Test
    fun `formattedDate includes relative keyword for today`() {
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(
            id = 1L,
            text = "test",
            postProcessedText = null,
            timestamp = now,
            isSaved = false,
            audioPath = null,
        )
        assertTrue(entry.formattedDate().startsWith("Today"))
    }
}
