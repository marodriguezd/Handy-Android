package com.handy.app.ui.postprocess

/**
 * Sprint 26 — Snapshot of a user's post-process form contents. Held
 * JVM-testable so [PostProcessFormValidator] runs against a stable
 * data class rather than a Compose-managed mutable State. Production
 * callers construct one of these from the SettingsStore fields at
 * the moment the user submits the form.
 */
internal data class PostProcessConfig(
    val provider: PostProcessProvider,
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
)

/**
 * Sprint 26 — Validator result. [Valid] is a singleton; [Invalid]
 * carries the list of failed check-names so the UI can highlight
 * the offending field(s) without re-running each check itself.
 */
internal sealed class PostProcessValidation {
    object Valid : PostProcessValidation()
    data class Invalid(val errors: List<String>) : PostProcessValidation()
}

/**
 * Sprint 26 — Pure validator. Three sub-checks:
 *   - [validateBaseUrl]: provider-specific scheme + non-blank
 *   - [validateModelId]: non-blank + safe characters (alnum +
 *     `.` `-` `_` `:` `/` `+` — Ollama tag-style `name:tag` is
 *     in scope, OpenAI style `gpt-4o-mini` is in scope)
 *   - [validateApiKey]: required iff [PostProcessProvider.requiresApiKey]
 *
 * [validateForm] composes the three and returns a [PostProcessValidation].
 *
 * All members are `internal` so the unit-test source-set can assert
 * each clause independently (mirrors the Sprint 22 / 24 JVM-test
 * precedent).
 */
internal object PostProcessFormValidator {

    internal fun validateBaseUrl(
        provider: PostProcessProvider,
        baseUrl: String,
    ): Boolean {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) return false
        return when (provider) {
            PostProcessProvider.OpenAI,
            PostProcessProvider.Anthropic -> {
                trimmed.startsWith("https://", ignoreCase = true) &&
                    trimmed.length > "https://x".length
            }
            PostProcessProvider.Ollama -> {
                // Ollama is local-by-default; allow http:// loopback OR
                // https:// (when self-hosted in a TLS-fronted deployment).
                (trimmed.startsWith("http://", ignoreCase = true) && trimmed.length > "http://x".length) ||
                    (trimmed.startsWith("https://", ignoreCase = true) && trimmed.length > "https://x".length)
            }
            PostProcessProvider.Custom -> {
                // Custom: any non-blank http(s). Lets users point at
                // self-hosted vLLM / llama.cpp-OAI / etc.
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true)
            }
        }
    }

    internal fun validateModelId(modelId: String): Boolean {
        if (modelId.isBlank()) return false
        // Accept letters, digits, `.`, `-`, `_`, `:`, `/`, `+`.
        // - Ollama tag-style "llama3.2:3b" needs ':'.
        // - Hub-styled "org/model" needs '/'.
        // - Quantized suffix "Q4_K_M" needs '_'.
        return modelId.all { c -> c.isLetterOrDigit() || c in SAFE_MODEL_CHARS }
    }

    internal fun validateApiKey(
        provider: PostProcessProvider,
        apiKey: String,
    ): Boolean = if (provider.requiresApiKey) {
        apiKey.isNotBlank()
    } else {
        true
    }

    internal fun validateForm(config: PostProcessConfig): PostProcessValidation {
        val errors = buildList {
            if (!validateBaseUrl(config.provider, config.baseUrl)) add("baseUrl")
            if (!validateModelId(config.modelId)) add("modelId")
            if (!validateApiKey(config.provider, config.apiKey)) add("apiKey")
        }
        return if (errors.isEmpty()) {
            PostProcessValidation.Valid
        } else {
            PostProcessValidation.Invalid(errors)
        }
    }

    private const val SAFE_MODEL_CHARS = ".-_:/+"
}
