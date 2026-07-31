package com.handy.android

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionEngineTest {
    private val temporaryDirectories = mutableListOf<File>()

    @After
    fun cleanUp() {
        temporaryDirectories.asReversed().forEach { directory ->
            directory.walkBottomUp().forEach(File::delete)
        }
    }

    @Test
    fun isSupportedModelReturnsTrueOnlyForBinFiles() {
        val binFile = temporaryFile("model.bin", "data")
        val txtFile = temporaryFile("model.txt", "data")
        val noExtFile = temporaryFile("model", "data")

        assertTrue(TranscriptionEngine.isSupportedModel(binFile))
        assertFalse(TranscriptionEngine.isSupportedModel(txtFile))
        assertFalse(TranscriptionEngine.isSupportedModel(noExtFile))
    }

    @Test
    fun isValidatedModelRequiresRecordedDigest() {
        val binFile = temporaryFile("model.bin", "valid model content")
        assertFalse(TranscriptionEngine.isValidatedModel(binFile))

        val result = ModelValidationResult(ModelValidator.sha256(binFile), binFile.length())
        ModelValidator.writeDigestFile(binFile, result)
        assertTrue(TranscriptionEngine.isValidatedModel(binFile))
    }

    private fun temporaryFile(name: String, content: String): File {
        val directory = Files.createTempDirectory("handy-transcription-test").toFile()
        temporaryDirectories += directory
        return File(directory, name).apply { writeText(content) }
    }
}
