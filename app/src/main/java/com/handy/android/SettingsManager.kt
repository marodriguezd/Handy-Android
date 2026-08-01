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
    private const val POST_PROCESSING_ENABLED = "post_processing_enabled"
    private const val AUTO_CAPITALIZATION_ENABLED = "auto_capitalization_enabled"
    private const val PUNCTUATION_CLEANUP_ENABLED = "punctuation_cleanup_enabled"
    private const val SOUND_FEEDBACK_ENABLED = "sound_feedback_enabled"
    private const val HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
    private const val LLM_ENABLED = "llm_enabled"
    private const val LLM_ENDPOINT = "llm_endpoint"
    private const val LLM_API_KEY = "llm_api_key"
    private const val LLM_MODEL = "llm_model"
    private const val LLM_SYSTEM_PROMPT = "llm_system_prompt"
    private const val FEEDBACK_ENABLED = "1"
    private const val DEFAULT_LLM_ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val DEFAULT_LLM_MODEL = "gpt-4o-mini"
    private const val DEFAULT_LLM_PROMPT = "You are an expert editor. Correct spelling and grammar without changing the meaning. Return only the edited text."

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

    /** Whether local vocabulary and text cleanup should be applied to transcriptions. */
    fun postProcessingEnabled(context: Context): Boolean =
        booleanSetting(context, POST_PROCESSING_ENABLED, default = true)

    fun setPostProcessingEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, POST_PROCESSING_ENABLED), enabled)

    /** Whether sentence and isolated-`i` capitalization should be applied. */
    fun autoCapitalizationEnabled(context: Context): Boolean =
        booleanSetting(context, AUTO_CAPITALIZATION_ENABLED, default = true)

    fun setAutoCapitalizationEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, AUTO_CAPITALIZATION_ENABLED), enabled)

    /** Whether punctuation spacing and duplicate whitespace should be normalized. */
    fun punctuationCleanupEnabled(context: Context): Boolean =
        booleanSetting(context, PUNCTUATION_CLEANUP_ENABLED, default = true)

    fun setPunctuationCleanupEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, PUNCTUATION_CLEANUP_ENABLED), enabled)

    /** Whether short sound cues should be played for recording events. */
    fun soundFeedbackEnabled(context: Context): Boolean =
        file(context, SOUND_FEEDBACK_ENABLED).readTextOrNull()?.trim() != "0"

    fun setSoundFeedbackEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, SOUND_FEEDBACK_ENABLED), enabled)

    /** Whether haptic cues should be emitted for recording events. */
    fun hapticFeedbackEnabled(context: Context): Boolean =
        file(context, HAPTIC_FEEDBACK_ENABLED).readTextOrNull()?.trim() != "0"

    fun setHapticFeedbackEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, HAPTIC_FEEDBACK_ENABLED), enabled)

    fun llmEnabled(context: Context): Boolean =
        booleanSetting(context, LLM_ENABLED, default = false)

    fun setLlmEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, LLM_ENABLED), enabled)

    fun llmEndpoint(context: Context): String =
        file(context, LLM_ENDPOINT).readTextOrNull()?.trim().orEmpty().ifBlank { DEFAULT_LLM_ENDPOINT }

    fun setLlmEndpoint(context: Context, endpoint: String) =
        file(context, LLM_ENDPOINT).writeText(endpoint.trim())

    fun llmApiKey(context: Context): String =
        file(context, LLM_API_KEY).readTextOrNull()?.trim()?.let(::decodeApiKey).orEmpty()

    fun setLlmApiKey(context: Context, apiKey: String) {
        val target = file(context, LLM_API_KEY)
        if (apiKey.isBlank()) target.delete() else target.writeText(encodeApiKey(apiKey.trim()))
    }

    fun llmModel(context: Context): String =
        file(context, LLM_MODEL).readTextOrNull()?.trim().orEmpty().ifBlank { DEFAULT_LLM_MODEL }

    fun setLlmModel(context: Context, model: String) =
        file(context, LLM_MODEL).writeText(model.trim())

    fun llmSystemPrompt(context: Context): String =
        file(context, LLM_SYSTEM_PROMPT).readTextOrNull()?.trim().orEmpty().ifBlank { DEFAULT_LLM_PROMPT }

    fun setLlmSystemPrompt(context: Context, prompt: String) =
        file(context, LLM_SYSTEM_PROMPT).writeText(prompt.trim())

    private fun booleanSetting(context: Context, name: String, default: Boolean): Boolean =
        when (file(context, name).readTextOrNull()?.trim()) {
            FEEDBACK_ENABLED -> true
            "0" -> false
            else -> default
        }

    private fun setBoolean(target: File, enabled: Boolean) {
        if (enabled) target.writeText(FEEDBACK_ENABLED) else target.writeText("0")
    }

    private fun File.readTextOrNull(): String? = takeIf { isFile }?.readText()

    @Suppress("unused")
    fun encodeApiKey(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())

    @Suppress("unused")
    fun decodeApiKey(value: String): String = runCatching {
        String(Base64.getDecoder().decode(value))
    }.getOrDefault("")
}
