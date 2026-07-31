package com.handy.android

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelValidatorTest {
    private val temporaryDirectories = mutableListOf<File>()

    @After
    fun cleanUp() {
        temporaryDirectories.asReversed().forEach { directory ->
            directory.walkBottomUp().forEach(File::delete)
        }
    }

    @Test
    fun sha256MatchesKnownDigest() {
        val file = temporaryFile("Handy model")

        assertEquals(
            "${sha256("Handy model".toByteArray())}",
            ModelValidator.sha256(file),
        )
    }

    @Test
    fun expectedSha256RejectsChangedContent() {
        val file = temporaryFile("first")
        val expected = ModelValidator.sha256(file)
        file.writeText("changed")

        try {
            ModelValidator.verifyExpectedSha256(file, expected)
            throw AssertionError("Expected a SHA-256 mismatch")
        } catch (error: ModelValidationException) {
            assertTrue(error.message.orEmpty().contains("SHA-256 mismatch"))
        }
    }

    @Test
    fun recordedDigestDetectsTampering() {
        val file = temporaryFile("validated model")
        val result = ModelValidationResult(ModelValidator.sha256(file), file.length())
        ModelValidator.writeDigestFile(file, result)

        assertEquals(result.sha256, ModelValidator.readRecordedDigest(file))
        assertTrue(ModelValidator.verifyRecordedDigest(file))

        file.appendText("tampered")

        assertFalse(ModelValidator.verifyRecordedDigest(file))
    }

    @Test
    fun recordedDigestIsBoundToItsModelFileName() {
        val file = temporaryFile("model")
        val result = ModelValidationResult(ModelValidator.sha256(file), file.length())
        val digestFile = ModelValidator.digestFile(file)
        digestFile.writeText("${result.sha256}  another-model.bin\n")

        assertEquals(null, ModelValidator.readRecordedDigest(file))
    }

    @Test
    fun validateSucceedsWithMockEngine() {
        val file = temporaryFile("mock model data")
        val expectedSha = ModelValidator.sha256(file)
        var closed = false
        var initializedPath: String? = null

        val mockEngine = object : IWhisperEngine {
            override fun init(modelPath: String): Boolean {
                initializedPath = modelPath
                return true
            }

            override fun transcribe(
                audioData: FloatArray,
                numThreads: Int,
                translate: Boolean,
                language: String,
            ): String = "mock result"

            override fun close() {
                closed = true
            }
        }

        val result = ModelValidator.validate(file, expectedSha, engineFactory = { mockEngine })
        assertEquals(expectedSha, result.sha256)
        assertEquals(file.length(), result.sizeBytes)
        assertEquals(file.absolutePath, initializedPath)
        assertTrue(closed)
    }

    @Test
    fun validateFailsWhenEngineRejectsModel() {
        val file = temporaryFile("corrupt model")
        val mockEngine = object : IWhisperEngine {
            override fun init(modelPath: String): Boolean = false
            override fun transcribe(
                audioData: FloatArray,
                numThreads: Int,
                translate: Boolean,
                language: String,
            ): String = ""

            override fun close() {}
        }

        try {
            ModelValidator.validate(file, engineFactory = { mockEngine })
            throw AssertionError("Expected ModelValidationException when init returns false")
        } catch (error: ModelValidationException) {
            assertTrue(error.message.orEmpty().contains("Whisper rejected model"))
        }
    }

    private fun temporaryFile(content: String): File {
        val directory = Files.createTempDirectory("handy-model-test").toFile()
        temporaryDirectories += directory
        return File(directory, "model.bin").apply { writeText(content) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}
