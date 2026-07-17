package com.handy.app.ui.postprocess

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.HandyDropdown

/**
 * Sprint 26 — Provider picker on the new top-level Post-Process
 * destination. Backed by [HandyDropdown] (MD3-native per Sprint 18
 * shared-components layer).
 *
 * On selection, the parent owns the persistence side-effect — this
 * composable is stateless. Modeled after [com.handy.app.ui.about.
 * components.ThemeSelector] from Sprint 23.
 */
@Composable
fun ProviderSelect(
    selected: PostProcessProvider,
    onSelect: (PostProcessProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    HandyDropdown(
        label = stringResource(R.string.postprocess_provider_label),
        options = PostProcessProvider.entries.map { provider ->
            provider to stringResource(labelResFor(provider))
        },
        selected = selected,
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun labelResFor(provider: PostProcessProvider): Int = when (provider) {
    PostProcessProvider.OpenAI -> R.string.postprocess_provider_openai
    PostProcessProvider.Anthropic -> R.string.postprocess_provider_anthropic
    PostProcessProvider.Ollama -> R.string.postprocess_provider_ollama
    PostProcessProvider.Custom -> R.string.postprocess_provider_custom
}
