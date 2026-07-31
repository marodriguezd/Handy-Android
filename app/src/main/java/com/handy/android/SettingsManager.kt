package com.handy.android

import android.content.Context
import java.io.File
import java.util.Base64

/** Small file-backed settings store shared by the app, recognition service, and IME process. */
object SettingsManager {
    private const val ACTIVE_MODEL = "active_model"
    private const val MODEL_LANGUAGE = "model_language"
    private const val MODEL_THREADS = "model_threads"
    private const val MODEL_TRANSLATE = "model_translate"
    private const val CUSTOM_WORDS = "custom_words"

    private fun file(context: Context, name: String) = File(context.filesDir, name)

    fun activeModelName(context: Context): String? =
        file(context, ACTIVE_MODEL).takeIf { it.isFile }?.readText()?.trim()
            ?.takeIf { it.isNotEmpty() && it == File(it).name && it.endsWith(".bin", ignoreCase = true) }

    /**
     * Records the active model only when its file still matches a locally
     * recorded digest produced after successful Whisper validation.
     */
    internal fun setActiveModel(
        context: Context,
        fileName: String,
        validation: ModelValidationResult,
    ) {
        require(fileName == File(fileName).name && fileName.endsWith(".bin", ignoreCase = true)) {
            "Invalid model file name: $fileName"
        }
        val model = File(File(context.filesDir, "models"), fileName)
        check(model.isFile && model.length() == validation.sizeBytes) {
            "Validated model changed before activation: $fileName"
        }
        check(ModelValidator.verifyRecordedDigest(model) && ModelValidator.readRecordedDigest(model) == validation.sha256) {
            "Model digest changed before activation: $fileName"
        }
        file(context, ACTIVE_MODEL).writeText(fileName)
    }

    fun clearActiveModel(context: Context) = file(context, ACTIVE_MODEL).delete()

    fun language(context: Context): String =
        file(context, MODEL_LANGUAGE).takeIf { it.isFile }?.readText()?.trim().orEmpty().ifBlank { "auto" }

    fun setLanguage(context: Context, language: String) = file(context, MODEL_LANGUAGE).writeText(language.trim())

    fun threadCount(context: Context): Int? =
        file(context, MODEL_THREADS).takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    fun setThreadCount(context: Context, threads: Int?) {
        val marker = file(context, MODEL_THREADS)
        if (threads == null || threads <= 0) marker.delete() else marker.writeText(threads.toString())
    }

    fun translate(context: Context): Boolean = file(context, MODEL_TRANSLATE).readTextOrNull() == "1"

    fun setTranslate(context: Context, enabled: Boolean) {
        val marker = file(context, MODEL_TRANSLATE)
        if (enabled) marker.writeText("1") else marker.delete()
    }

    fun customWords(context: Context): List<String> = file(context, CUSTOM_WORDS)
        .readTextOrNull()
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toList()
        .orEmpty()

    fun setCustomWords(context: Context, words: List<String>) =
        file(context, CUSTOM_WORDS).writeText(words.map(String::trim).filter(String::isNotEmpty).distinct().joinToString("\n"))

    private fun File.readTextOrNull(): String? = takeIf { isFile }?.readText()

    @Suppress("unused")
    fun encodeApiKey(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())

    @Suppress("unused")
    fun decodeApiKey(value: String): String = runCatching {
        String(Base64.getDecoder().decode(value))
    }.getOrDefault("")
}
