package com.handy.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class NoModelException : IllegalStateException("No downloaded model is selected")
class UnsupportedModelException(fileName: String) :
    IllegalArgumentException("Unsupported Whisper model format: $fileName. Use a GGML .bin model.")

object TranscriptionEngine {
    fun isSupportedModel(file: File): Boolean = file.isFile && file.extension.equals("bin", ignoreCase = true)

    fun isValidatedModel(file: File): Boolean =
        isSupportedModel(file) && ModelValidator.verifyRecordedDigest(file)

    suspend fun transcribe(context: Context, samples: FloatArray): String = withContext(Dispatchers.Default) {
        val model = selectedModel(context) ?: throw NoModelException()
        if (!isValidatedModel(model)) {
            throw ModelValidationException("Selected model has not passed integrity validation: ${model.name}")
        }
        WhisperLib().use { whisper ->
            check(whisper.init(model.absolutePath)) { "Unable to initialize the selected model" }
            whisper.transcribe(
                audioData = samples,
                numThreads = SettingsManager.threadCount(context)
                    ?: Runtime.getRuntime().availableProcessors().coerceAtMost(8),
                translate = SettingsManager.translate(context),
                language = SettingsManager.language(context),
            ).trim()
        }
    }

    fun selectedModel(context: Context): File? {
        val directory = File(context.filesDir, "models")
        val selected = SettingsManager.activeModelName(context)
            ?.let { File(directory, it) }
            ?.takeIf(::isValidatedModel)
        return selected ?: directory.listFiles()
            ?.firstOrNull(::isValidatedModel)
    }
}
