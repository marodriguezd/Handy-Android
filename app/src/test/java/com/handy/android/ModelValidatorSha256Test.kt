package com.handy.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Plain-JVM coverage for [ModelValidator.sha256], the helper that verifies the
 * debug runtime model download before activation (P0.3). Known vectors pin the
 * implementation; the mismatch case models a truncated/corrupted download, which
 * must never be accepted as the active model.
 *
 * Port of `FileSha256Test.java` from android_transcribe_app, adapted to the
 * destination's [ModelValidator] (which already provides SHA-256 hashing).
 */
class ModelValidatorSha256Test {

    @Test
    fun knownVectorEmptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ModelValidator.sha256(ByteArrayInputStream(ByteArray(0))),
        )
    }

    @Test
    fun knownVectorAbc() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ModelValidator.sha256(
                ByteArrayInputStream("abc".toByteArray(StandardCharsets.US_ASCII)),
            ),
        )
    }

    @Test
    fun truncatedContentHashesDiffer() {
        // A truncated model file (the P0.3 failure mode) must hash differently
        // from the complete file, so the download gate rejects it.
        val full = ModelValidator.sha256(
            ByteArrayInputStream("hello world".toByteArray(StandardCharsets.US_ASCII)),
        )
        val truncated = ModelValidator.sha256(
            ByteArrayInputStream("hello".toByteArray(StandardCharsets.US_ASCII)),
        )
        assertNotEquals(full, truncated)
    }

    @Test
    fun fileVariantMatchesStreamVariant() {
        val file = File.createTempFile("sha256", ".bin")
        try {
            Files.write(file.toPath(), "content".toByteArray(StandardCharsets.US_ASCII))
            assertEquals(
                ModelValidator.sha256(
                    ByteArrayInputStream("content".toByteArray(StandardCharsets.US_ASCII)),
                ),
                ModelValidator.sha256(file),
            )
        } finally {
            file.delete()
        }
    }
}
