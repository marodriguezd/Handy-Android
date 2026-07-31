package com.handy.android

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloaderTest {
    private val temporaryDirectories = mutableListOf<File>()

    @After
    fun cleanUp() {
        temporaryDirectories.asReversed().forEach { directory ->
            directory.walkBottomUp().forEach(File::delete)
        }
    }

    @Test
    fun modelPropertiesAndPathsAreCorrect() {
        val dir = temporaryDirectory()
        val model = ModelDownloader.Model(
            id = "tiny.en",
            displayName = "Whisper Tiny English",
            fileName = "ggml-tiny.en.bin",
            downloadUrl = "https://example.com/ggml-tiny.en.bin",
            catalogId = "whisper-tiny-en",
            description = "Tiny English model",
            parameters = "39M",
            languageCount = 1,
            downloadSizeBytes = 77_000_000L,
            expectedSha256 = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
        ).inDirectory(dir)

        assertEquals("ggml-tiny.en.bin", model.fileName)
        assertEquals(File(dir, "ggml-tiny.en.bin"), model.localFile)
        assertEquals("https://example.com/ggml-tiny.en.bin", model.downloadUrl)
    }

    private fun temporaryDirectory(): File {
        val directory = Files.createTempDirectory("handy-downloader-test").toFile()
        temporaryDirectories += directory
        return directory
    }
}
