package com.handy.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ModelInfo.formattedSize].
 */
class ModelInfoTest {

    private fun model(sizeBytes: Long) = ModelInfo(
        id = "handy-computer/test-gguf",
        displayName = "Test Model",
        sizeBytes = sizeBytes,
        language = "en",
        quant = "Q4_K_M",
        license = null,
        description = null,
        isDownloaded = false,
        isActive = false,
    )

    @Test
    fun `formattedSize returns bytes for small sizes`() {
        assertEquals("512 B", model(512L).formattedSize())
    }

    @Test
    fun `formattedSize returns kilobytes`() {
        assertEquals("1 KB", model(1_024L).formattedSize())
    }

    @Test
    fun `formattedSize returns megabytes`() {
        assertEquals("500 MB", model(500L * 1_048_576).formattedSize())
    }

    @Test
    fun `formattedSize returns gigabytes with one decimal`() {
        assertEquals("1.5 GB", model((1.5 * 1_073_741_824).toLong()).formattedSize())
    }

    @Test
    fun `formattedSize rounds gigabytes correctly`() {
        // 2 GB exactly
        assertEquals("2.0 GB", model(2L * 1_073_741_824).formattedSize())
    }
}
