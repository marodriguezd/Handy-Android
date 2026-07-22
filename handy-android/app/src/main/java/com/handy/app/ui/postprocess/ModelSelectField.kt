package com.handy.app.ui.postprocess

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R

/**
 * Sprint 26 — Model-id field on the Post-Process destination.
 *
 * Free-form text input (no provider-specific picker yet — Sprint 27+
 * could add a `HandyDropdown` listing the provider's catalog when
 * we wire a /models RPC discovery probe). Placeholder text reflects
 * the canonical id so a fresh-install user has a sensible default
 * (`gpt-4o-mini` for OpenAI, `llama3.2:3b` for Ollama, etc.).
 *
 * Stateless — the parent owns the value + persistence side-effect.
 *
 * Tag-style Ollama ids (`name:tag`) are accepted by the validator
 * because `validateModelId` allows the `:` character.
 */
@Composable
fun ModelSelectField(
    value: String,
    onValueChange: (String) -> Unit,
    provider: PostProcessProvider,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(stringResource(R.string.postprocess_model_label)) },
        placeholder = { Text(placeholderFor(provider)) },
        isError = value.isNotBlank() && !PostProcessFormValidator.validateModelId(value),
        modifier = modifier.fillMaxWidth(),
    )
}

private fun placeholderFor(provider: PostProcessProvider): String = when (provider) {
    PostProcessProvider.OpenAI -> "gpt-4o-mini"
    PostProcessProvider.Anthropic -> "claude-3-5-sonnet"
    PostProcessProvider.Ollama -> "llama3.2:3b"
    PostProcessProvider.MiniMax -> "MiniMax-Text-01"
    PostProcessProvider.Cohere -> "command-r-plus"
    PostProcessProvider.Custom -> "my-model"
}
