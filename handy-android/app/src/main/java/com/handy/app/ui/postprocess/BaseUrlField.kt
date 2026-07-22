package com.handy.app.ui.postprocess

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R

/**
 * Sprint 26 — Endpoint URL field on the Post-Process destination.
 *
 * Placeholder shifts per provider so the user sees the canonical
 * endpoint URL right under the input (`api.openai.com/v1/chat/completions`
 * for OpenAI, `10.0.2.2:11434/api/chat` for Ollama, etc.). The
 * [isError] flag is driven by [PostProcessFormValidator.validateBaseUrl]
 * so a misspelled scheme lights up immediately.
 *
 * Stateless — the parent owns the value + persistence side-effect.
 */
@Composable
fun BaseUrlField(
    value: String,
    onValueChange: (String) -> Unit,
    provider: PostProcessProvider,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(stringResource(R.string.postprocess_baseurl_label)) },
        placeholder = { Text(stringResource(hintResFor(provider))) },
        supportingText = { Text(stringResource(R.string.postprocess_baseurl_supporting)) },
        isError = value.isNotBlank() && !PostProcessFormValidator.validateBaseUrl(provider, value),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun hintResFor(provider: PostProcessProvider): Int = when (provider) {
    PostProcessProvider.OpenAI -> R.string.postprocess_baseurl_hint_openai
    PostProcessProvider.Anthropic -> R.string.postprocess_baseurl_hint_anthropic
    PostProcessProvider.Ollama -> R.string.postprocess_baseurl_hint_ollama
    PostProcessProvider.MiniMax -> R.string.postprocess_baseurl_hint_minimax
    PostProcessProvider.Cohere -> R.string.postprocess_baseurl_hint_cohere
    PostProcessProvider.Custom -> R.string.postprocess_baseurl_hint_custom
}
