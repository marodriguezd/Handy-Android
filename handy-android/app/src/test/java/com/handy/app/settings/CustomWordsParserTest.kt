package com.handy.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 25b Phase E — covers the [String.parseCustomWords] helper.
 *
 * Test count: 5. Each test exercises a single contract clause in the
 * function's KDoc so a regression on any contract surfaces as a
 * single red test rather than a coarser failure mode.
 */
class CustomWordsParserTest {

    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList<String>(), "".parseCustomWords())
    }

    @Test
    fun `single word input returns single-element list`() {
        assertEquals(listOf("buffy"), "buffy".parseCustomWords())
    }

    @Test
    fun `comma-separated words are split and trimmed`() {
        val result = "Buffy,  iPhone  ,\nLlama".parseCustomWords()
        assertEquals(listOf("Buffy", "iPhone", "Llama"), result)
    }

    @Test
    fun `duplicates are preserved case-sensitively`() {
        // Per Sprint 25b Q7 verdict: keep case-sensitive literal
        // dedup so Whisper treats proper-noun casing as a separate
        // hot-prompt token.
        val result = "iPhone, iphone, IPHONE".parseCustomWords()
        assertEquals(listOf("iPhone", "iphone", "IPHONE"), result)
        assertEquals(3, result.size)
    }

    @Test
    fun `maxChars cap returns empty list when exceeded`() {
        // 600 chars > default 500. Constructor must short-circuit to
        // emptyList() so the SharedPreferences writer never sees an
        // unbounded payload.
        val bomb = "a".repeat(600)
        assertEquals(emptyList<String>(), bomb.parseCustomWords())
    }

    @Test
    fun `maxEntries cap truncates long input`() {
        // 60 comma-separated entries > default 50. The first 50 must
        // survive in input order; the trailing 10 are dropped.
        val input = (1..60).joinToString(",")
        val result = input.parseCustomWords()
        assertTrue("expected exactly 50 entries, got ${result.size}", result.size == 50)
        assertEquals("1", result.first())
        assertEquals("50", result.last())
    }
}
