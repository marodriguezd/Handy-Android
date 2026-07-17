package com.handy.app.ui.postprocess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprint 26 — 8 JVM tests for [PostProcessFormValidator].
 *
 * Coverage:
 *   - 4 baseURL tests: canonical URLs across the 4 providers
 *     (OpenAI https, Anthropic https, Ollama http loopback, Custom
 *     free-form).
 *   - 2 model-id tests: free-form alpha-numeric + Ollama tag-style
 *     `name:tag` accepted.
 *   - 1 api-key test: required for hosted providers, optional for
 *     loopback providers.
 *   - 1 happy-path validateForm round-trip: composes the three
 *     sub-checks into a Valid/Invalid result.
 *
 * Pure JVM (no Robolectric, no Android deps) — mirrors the
 * Sprint 22 / Sprint 24 / Sprint 25 JVM-test pattern.
 */
class PostProcessFormValidatorTest {

    @Test
    fun `OpenAI accepts canonical https base URL`() {
        assertTrue(
            PostProcessFormValidator.validateBaseUrl(
                PostProcessProvider.OpenAI,
                "https://api.openai.com/v1/chat/completions",
            ),
        )
    }

    @Test
    fun `Anthropic accepts canonical https base URL`() {
        assertTrue(
            PostProcessFormValidator.validateBaseUrl(
                PostProcessProvider.Anthropic,
                "https://api.anthropic.com/v1/messages",
            ),
        )
    }

    @Test
    fun `Ollama accepts http loopback base URL`() {
        // 10.0.2.2 is the Android emulator's host loopback — the
        // canonical Ollama dev URL.
        assertTrue(
            PostProcessFormValidator.validateBaseUrl(
                PostProcessProvider.Ollama,
                "http://10.0.2.2:11434/api/chat",
            ),
        )
        // localhost is also accepted (qemu-user-net on some host
        // setups, also Docker-for-Mac bridge).
        assertTrue(
            PostProcessFormValidator.validateBaseUrl(
                PostProcessProvider.Ollama,
                "http://localhost:11434/api/chat",
            ),
        )
    }

    @Test
    fun `Custom accepts any non-empty http(s) and rejects empty`() {
        assertTrue(
            PostProcessFormValidator.validateBaseUrl(
                PostProcessProvider.Custom,
                "https://my-custom.example.com/v1/chat",
            ),
        )
        assertTrue(
            PostProcessFormValidator.validateBaseUrl(
                PostProcessProvider.Custom,
                "http://192.168.1.10:8080/v1",
            ),
        )
        assertFalse(
            PostProcessFormValidator.validateBaseUrl(PostProcessProvider.Custom, ""),
        )
        assertFalse(
            PostProcessFormValidator.validateBaseUrl(PostProcessProvider.Custom, "   "),
        )
    }

    @Test
    fun `validateModelId accepts free-form alphanumeric and rejects blank`() {
        assertTrue(PostProcessFormValidator.validateModelId("gpt-4o-mini"))
        assertTrue(PostProcessFormValidator.validateModelId("claude-3-5-sonnet"))
        assertTrue(PostProcessFormValidator.validateModelId("Qwen2-VL-7B"))
        assertFalse(PostProcessFormValidator.validateModelId(""))
        assertFalse(PostProcessFormValidator.validateModelId("   "))
    }

    @Test
    fun `validateModelId accepts Ollama tag-style id`() {
        // Ollama uses `name:tag` (e.g., llama3.2:3b) — the validator
        // must accept the `:` character.
        assertTrue(PostProcessFormValidator.validateModelId("llama3.2:3b"))
        assertTrue(PostProcessFormValidator.validateModelId("qwen2:7b-instruct"))
        // Hub-styled "org/model" must also pass.
        assertTrue(PostProcessFormValidator.validateModelId("meta-llama/Llama-3-8B"))
    }

    @Test
    fun `validateApiKey is required for OpenAI and Anthropic but optional for Ollama and Custom`() {
        // Hosted providers: blank key rejected.
        assertFalse(PostProcessFormValidator.validateApiKey(PostProcessProvider.OpenAI, ""))
        assertFalse(PostProcessFormValidator.validateApiKey(PostProcessProvider.Anthropic, ""))
        assertTrue(PostProcessFormValidator.validateApiKey(PostProcessProvider.OpenAI, "sk-abc123"))
        // Loopback providers: blank key accepted.
        assertTrue(PostProcessFormValidator.validateApiKey(PostProcessProvider.Ollama, ""))
        assertTrue(PostProcessFormValidator.validateApiKey(PostProcessProvider.Custom, ""))
        assertTrue(PostProcessFormValidator.validateApiKey(PostProcessProvider.Ollama, "any-string"))
    }

    @Test
    fun `validateForm returns Valid for happy-path OpenAI config and Invalid when API key missing`() {
        val happy = PostProcessConfig(
            provider = PostProcessProvider.OpenAI,
            baseUrl = "https://api.openai.com/v1/chat/completions",
            apiKey = "sk-abc123",
            modelId = "gpt-4o-mini",
        )
        assertEquals(PostProcessValidation.Valid, PostProcessFormValidator.validateForm(happy))

        val missingKey = happy.copy(apiKey = "")
        val result = PostProcessFormValidator.validateForm(missingKey)
        assertTrue("expected Invalid when apiKey is blank", result is PostProcessValidation.Invalid)
        result as PostProcessValidation.Invalid
        assertTrue("expected 'apiKey' in errors", "apiKey" in result.errors)
    }
}
