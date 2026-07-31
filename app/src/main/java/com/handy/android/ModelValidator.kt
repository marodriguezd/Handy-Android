package com.handy.android

import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

class ModelValidationException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

data class ModelValidationResult(
    val sha256: String,
    val sizeBytes: Long,
)

/** Validates model integrity and format before a model is made active. */
object ModelValidator {
    private const val SHA256_LENGTH = 64
    private val digestPattern = Regex("[0-9a-fA-F]{$SHA256_LENGTH}")

    /** Loads the model through the native Whisper engine after checking its digest. */
    fun validate(
        file: File,
        expectedSha256: String? = null,
        engineFactory: () -> IWhisperEngine = { WhisperLib() },
    ): ModelValidationResult {
        require(file.isFile) { "Model file does not exist: ${file.name}" }
        require(file.length() > 0L) { "Model file is empty: ${file.name}" }

        val digest = verifyExpectedSha256(file, expectedSha256)
        try {
            engineFactory().use { whisper ->
                check(whisper.init(file.absolutePath)) {
                    "Whisper rejected model ${file.name}"
                }
            }
        } catch (error: ModelValidationException) {
            throw error
        } catch (error: Exception) {
            throw ModelValidationException(
                "Unable to load Whisper model ${file.name}: ${error.message ?: "invalid model"}",
                error,
            )
        } catch (error: LinkageError) {
            throw ModelValidationException(
                "Whisper native engine is unavailable while validating ${file.name}",
                error,
            )
        }

        return ModelValidationResult(digest, file.length())
    }

    /** Validates a model and atomically records the digest used for future integrity checks. */
    fun validateAndRecord(
        file: File,
        expectedSha256: String? = null,
        engineFactory: () -> IWhisperEngine = { WhisperLib() },
    ): ModelValidationResult {
        val result = validate(file, expectedSha256, engineFactory)
        writeDigestFile(file, result)
        return result
    }

    /** Returns true only when the model still matches its locally recorded validated digest. */
    fun verifyRecordedDigest(file: File): Boolean {
        if (!file.isFile) return false
        val recorded = readRecordedDigest(file) ?: return false
        return runCatching { sha256(file) == recorded }.getOrDefault(false)
    }

    fun writeDigestFile(file: File, result: ModelValidationResult) {
        val digestFile = digestFile(file)
        val temporary = File(digestFile.path + ".part")
        temporary.writeText(
            "${result.sha256}  ${file.name}\n",
            StandardCharsets.UTF_8,
        )
        try {
            try {
                Files.move(
                    temporary.toPath(),
                    digestFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), digestFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    fun readRecordedDigest(file: File): String? {
        val line = digestFile(file).takeIf { it.isFile }?.useLines { lines -> lines.firstOrNull() }
            ?.trim()
            ?: return null
        val parts = line.split(Regex("\\s+"), limit = 2)
        if (parts.size != 2 || parts[1].removePrefix("*") != file.name) return null
        return parts[0].takeIf { digestPattern.matches(it) }?.lowercase(Locale.US)
    }

    /** Calculates and, when supplied, strictly compares the SHA-256 of a model. */
    fun verifyExpectedSha256(file: File, expectedSha256: String? = null): String {
        val digest = sha256(file)
        val expected = expectedSha256?.trim()?.lowercase(Locale.US)
        if (expected != null) {
            require(digestPattern.matches(expected)) {
                "Invalid expected SHA-256 for ${file.name}"
            }
            if (digest != expected) {
                throw ModelValidationException(
                    "SHA-256 mismatch for ${file.name}: expected $expected, got $digest",
                )
            }
        }
        return digest
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    fun digestFile(file: File): File = File(file.path + ".sha256")
}
