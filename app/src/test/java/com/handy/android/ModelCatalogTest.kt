package com.handy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun catalogContainsOnlyModelsWithinMobileParameterLimit() {
        assertTrue(ModelCatalog.models.isNotEmpty())
        assertTrue(ModelCatalog.models.all { it.parameterCount <= ModelCatalog.MAX_PARAMETERS })
    }

    @Test
    fun catalogIdsAreUnique() {
        val ids = ModelCatalog.models.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun onlyWhisperModelsAreDownloadableByCurrentAndroidBackend() {
        assertTrue(ModelCatalog.downloadableModels.isNotEmpty())
        assertTrue(ModelCatalog.downloadableModels.all { it.architecture == "whisper" })
        assertTrue(ModelCatalog.downloadableModels.all { it.isAvailableOnAndroid })
        assertFalse(ModelCatalog.models.any { it.architecture != "whisper" && it.isAvailableOnAndroid })
    }

    @Test
    fun downloadableSpecsHaveSecureUrlsAndVerifiedChecksums() {
        ModelCatalog.downloadableModels.forEach { entry ->
            val download = requireNotNull(entry.androidDownload)
            assertTrue(download.url.startsWith("https://"))
            assertTrue(download.fileName.endsWith(".bin"))
            assertTrue(download.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(download.id.isNotBlank())
        }
    }

    @Test
    fun downloadableSpecsUseDistinctAndroidFilesAndMatchDisplayedSizes() {
        val downloads = ModelCatalog.downloadableModels.mapNotNull { entry ->
            assertEquals(entry.downloadSizeBytes, entry.androidDownload?.sizeBytes)
            entry.androidDownload
        }
        assertEquals(downloads.size, downloads.distinctBy { it.fileName }.size)
        assertEquals(downloads.size, downloads.distinctBy { it.id }.size)
    }

    @Test
    fun catalogSourceMetadataIsGeneratedFromDesktopSnapshot() {
        assertTrue(ModelCatalog.SOURCE_CATALOG_VERSION > 0)
        assertTrue(ModelCatalog.SOURCE_CATALOG_GENERATED_AT.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.*")))
    }

    @Test
    fun storefrontFormatsModelSizesForUsers() {
        assertEquals("48 MB", formatModelSize(50_462_816L))
        assertEquals("1.5 GB", formatModelSize(1_549L * 1024 * 1024))
    }
}
