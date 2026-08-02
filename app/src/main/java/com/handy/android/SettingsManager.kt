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
    private const val INITIAL_PROMPT = "initial_prompt"
    private const val MODEL_UNLOAD_TIMEOUT_MS = "model_unload_timeout_ms"
    private const val GPU_BACKEND = "gpu_backend"
    private const val EXTRA_RECORDING_BUFFER_MS = "extra_recording_buffer_ms"
    private const val MUTE_WHILE_RECORDING = "mute_while_recording"
    private const val INPUT_DEVICE_ID = "input_device_id"
    private const val AUTO_SUBMIT = "auto_submit"
    private const val REMOVE_FILLER_WORDS = "remove_filler_words"
    private const val TRIM_TRAILING_SPACE = "trim_trailing_space"
    private const val AUTO_START_ON_BOOT = "auto_start_on_boot"
    private const val CUSTOM_WORDS = "custom_words"
    private const val LLM_PROMPT_TEMPLATES = "llm_prompt_templates"
    private const val POST_PROCESSING_ENABLED = "post_processing_enabled"
    private const val AUTO_CAPITALIZATION_ENABLED = "auto_capitalization_enabled"
    private const val PUNCTUATION_CLEANUP_ENABLED = "punctuation_cleanup_enabled"
    private const val SOUND_FEEDBACK_ENABLED = "sound_feedback_enabled"
    private const val HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
    private const val DYNAMIC_COLOR_ENABLED = "dynamic_color_enabled"
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

    fun initialPrompt(context: Context): String = file(context, INITIAL_PROMPT).readTextOrNull()?.trim().orEmpty()

    fun setInitialPrompt(context: Context, prompt: String) {
        val target = file(context, INITIAL_PROMPT)
        if (prompt.isBlank()) target.delete() else target.writeText(prompt.trim())
    }

    fun modelUnloadTimeoutMs(context: Context): Long =
        file(context, MODEL_UNLOAD_TIMEOUT_MS).readTextOrNull()?.trim()?.toLongOrNull()
            ?.coerceIn(0L, 60L * 60L * 1_000L) ?: 0L

    fun setModelUnloadTimeoutMs(context: Context, timeoutMs: Long) =
        file(context, MODEL_UNLOAD_TIMEOUT_MS).writeText(timeoutMs.coerceAtLeast(0L).toString())

    fun gpuBackend(context: Context): String =
        file(context, GPU_BACKEND).readTextOrNull()?.trim()?.lowercase().takeUnless { it.isNullOrBlank() } ?: "cpu"

    fun setGpuBackend(context: Context, backend: String) {
        file(context, GPU_BACKEND).writeText(if (backend.equals("vulkan", true)) "vulkan" else "cpu")
    }

    fun extraRecordingBufferMs(context: Context): Long =
        file(context, EXTRA_RECORDING_BUFFER_MS).readTextOrNull()?.trim()?.toLongOrNull()
            ?.coerceIn(0L, 2_000L) ?: 300L

    fun setExtraRecordingBufferMs(context: Context, value: Long) =
        file(context, EXTRA_RECORDING_BUFFER_MS).writeText(value.coerceIn(0L, 2_000L).toString())

    fun muteWhileRecording(context: Context): Boolean =
        booleanSetting(context, MUTE_WHILE_RECORDING, default = true)

    fun setMuteWhileRecording(context: Context, enabled: Boolean) =
        setBoolean(file(context, MUTE_WHILE_RECORDING), enabled)

    fun inputDeviceId(context: Context): Int? =
        file(context, INPUT_DEVICE_ID).readTextOrNull()?.trim()?.toIntOrNull()?.takeIf { it >= 0 }

    fun setInputDeviceId(context: Context, deviceId: Int?) {
        val target = file(context, INPUT_DEVICE_ID)
        if (deviceId == null) target.delete() else target.writeText(deviceId.toString())
    }

    fun autoSubmitEnabled(context: Context): Boolean =
        booleanSetting(context, AUTO_SUBMIT, default = false)

    fun setAutoSubmitEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, AUTO_SUBMIT), enabled)

    fun removeFillerWordsEnabled(context: Context): Boolean =
        booleanSetting(context, REMOVE_FILLER_WORDS, default = false)

    fun setRemoveFillerWordsEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, REMOVE_FILLER_WORDS), enabled)

    fun trimTrailingSpaceEnabled(context: Context): Boolean =
        booleanSetting(context, TRIM_TRAILING_SPACE, default = true)

    fun setTrimTrailingSpaceEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, TRIM_TRAILING_SPACE), enabled)

    fun autoStartOnBoot(context: Context): Boolean =
        booleanSetting(context, AUTO_START_ON_BOOT, default = false)

    fun setAutoStartOnBoot(context: Context, enabled: Boolean) =
        setBoolean(file(context, AUTO_START_ON_BOOT), enabled)

    data class PromptTemplate(val name: String, val prompt: String)

    fun llmPromptTemplates(context: Context): List<PromptTemplate> = file(context, LLM_PROMPT_TEMPLATES)
        .readTextOrNull()
        ?.lineSequence()
        ?.mapNotNull { line ->
            val separator = line.indexOf('\t')
            if (separator <= 0) return@mapNotNull null
            runCatching {
                PromptTemplate(
                    String(Base64.getDecoder().decode(line.substring(0, separator))),
                    String(Base64.getDecoder().decode(line.substring(separator + 1))),
                )
            }.getOrNull()
        }
        ?.filter { it.name.isNotBlank() && it.prompt.isNotBlank() }
        ?.toList()
        .orEmpty()

    fun setLlmPromptTemplates(context: Context, templates: List<PromptTemplate>) {
        file(context, LLM_PROMPT_TEMPLATES).writeText(
            templates.filter { it.name.isNotBlank() && it.prompt.isNotBlank() }
                .distinctBy { it.name.trim().lowercase() }
                .joinToString("\n") { template ->
                    "${Base64.getEncoder().encodeToString(template.name.trim().toByteArray())}\t" +
                        Base64.getEncoder().encodeToString(template.prompt.trim().toByteArray())
                },
        )
    }

    fun saveLlmPromptTemplate(context: Context, template: PromptTemplate) =
        setLlmPromptTemplates(context, llmPromptTemplates(context).filterNot { it.name.equals(template.name, true) } + template)

    fun deleteLlmPromptTemplate(context: Context, name: String) =
        setLlmPromptTemplates(context, llmPromptTemplates(context).filterNot { it.name.equals(name, true) })

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

    /** Whether the Material You dynamic colour scheme should replace the fixed brand palette (API 31+). */
    fun dynamicColorEnabled(context: Context): Boolean =
        booleanSetting(context, DYNAMIC_COLOR_ENABLED, default = false)

    fun setDynamicColorEnabled(context: Context, enabled: Boolean) =
        setBoolean(file(context, DYNAMIC_COLOR_ENABLED), enabled)

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
