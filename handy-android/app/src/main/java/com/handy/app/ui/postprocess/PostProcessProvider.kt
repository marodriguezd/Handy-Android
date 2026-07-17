package com.handy.app.ui.postprocess

/**
 * Sprint 26 — Canonical post-process provider enum.
 *
 * Each entry carries:
 *   - [defaultBaseUrl]: the canonical URL [ProviderSelect] autoloads when
 *     the user picks this provider. Users can override via [BaseUrlField]
 *     if their deployment uses a different endpoint (e.g., self-hosted
 *     Ollama on a remote IP, or a local keymirror of OpenAI).
 *   - [requiresApiKey]: true for hosted APIs (OpenAI, Anthropic), false
 *     for loopback-only inference (Ollama, Custom).
 *
 * Persistence: [SettingsStore.postProcessProviderId] stores the
 * lowercase [name]; [fromToken] rehydrates on first read.
 */
enum class PostProcessProvider(
    val defaultBaseUrl: String,
    val requiresApiKey: Boolean,
) {
    OpenAI(
        defaultBaseUrl = "https://api.openai.com/v1/chat/completions",
        requiresApiKey = true,
    ),
    Anthropic(
        defaultBaseUrl = "https://api.anthropic.com/v1/messages",
        requiresApiKey = true,
    ),
    Ollama(
        defaultBaseUrl = "http://10.0.2.2:11434/api/chat",
        requiresApiKey = false,
    ),
    Custom(
        defaultBaseUrl = "",
        requiresApiKey = false,
    ),
    ;

    companion object {
        /**
         * Rehydrate from the lowercase token persisted by
         * [SettingsStore.postProcessProviderId]. Unknown / corrupt values
         * resolve to [Custom] so a stale legacy sharedpref entry never
         * crashes the provider dropdown on first read.
         */
        fun fromToken(token: String?): PostProcessProvider = when (token?.lowercase()) {
            "openai" -> OpenAI
            "anthropic" -> Anthropic
            "ollama" -> Ollama
            else -> Custom
        }

        /**
         * The lowercase token to persist. Pure function — used by
         * [PostProcessScreen] whenever the user picks a new provider.
         */
        fun tokenFor(provider: PostProcessProvider): String = provider.name.lowercase()
    }
}
