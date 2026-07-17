package com.handy.app.ui.postprocess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.ui.components.SettingsGroup
import com.handy.app.ui.components.Spacing

/**
 * Sprint 26 — Top-level Post-Process destination composable.
 *
 * Promoted out of the ModelsTabsScreen into its own
 * `Screen.PostProcess` nav-rail destination. Reads:
 *   - `settingsStore.postProcessProviderId`
 *   - `settingsStore.postProcessEndpoint`
 *   - `settingsStore.postProcessApiKey`
 *   - `settingsStore.postProcessModel`
 *   - `settingsStore.postProcessPrompts` (new Sprint 26 field — see
 *     SettingsStore.kt)
 *
 * Every mutation writes back to the SettingsStore via the same field
 * setter, so the persistence-side-effect is co-located with the
 * UI-side read. Provider changes also auto-fill the base URL when
 * the user hasn't manually overridden it (we detect "untouched" by
 * comparing the current value against the union of all known
 * defaultBaseUrl values).
 */
@Composable
fun PostProcessScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as HandyApplication
    val store = app.settingsStore

    var provider by remember {
        mutableStateOf(PostProcessProvider.fromToken(store.postProcessProviderId))
    }
    var baseUrl by remember {
        mutableStateOf(
            store.postProcessEndpoint.ifBlank { provider.defaultBaseUrl },
        )
    }
    var apiKey by remember { mutableStateOf(store.postProcessApiKey) }
    var modelId by remember { mutableStateOf(store.postProcessModel) }
    var prompts by remember {
        mutableStateOf(
            store.postProcessPrompts.mapIndexed { index, text ->
                PostProcessPrompt(
                    id = "p$index",
                    name = "Prompt ${index + 1}",
                    text = text,
                )
            },
        )
    }
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PostProcessPrompt?>(null) }

    // On first composition: if the persisted baseUrl is blank,
    // auto-load the provider's default so a fresh-install user
    // sees a sensible value.
    LaunchedEffect(provider) {
        if (baseUrl.isBlank()) {
            baseUrl = provider.defaultBaseUrl
            store.postProcessEndpoint = baseUrl
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        SettingsGroup(title = stringResource(R.string.postprocess_section_provider)) {
            ProviderSelect(
                selected = provider,
                onSelect = { newProvider ->
                    provider = newProvider
                    store.postProcessProviderId = PostProcessProvider.tokenFor(newProvider)
                    if (baseUrl.isBlank() || baseUrl in allDefaults()) {
                        baseUrl = newProvider.defaultBaseUrl
                        store.postProcessEndpoint = baseUrl
                    }
                },
            )
        }
        SettingsGroup(title = stringResource(R.string.postprocess_section_endpoint)) {
            BaseUrlField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    store.postProcessEndpoint = it
                },
                provider = provider,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            ApiKeyField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    store.postProcessApiKey = it
                },
                provider = provider,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            ModelSelectField(
                value = modelId,
                onValueChange = {
                    modelId = it
                    store.postProcessModel = it
                },
                provider = provider,
            )
        }
        PromptList(
            prompts = prompts,
            onAdd = {
                editing = null
                editorVisible = true
            },
            onEdit = {
                editing = it
                editorVisible = true
            },
            onDelete = { prompt ->
                prompts = prompts.filterNot { it.id == prompt.id }
                store.postProcessPrompts = prompts.map { it.text }
            },
        )
    }

    PromptEditor(
        visible = editorVisible,
        initial = editing,
        onSave = { saved ->
            val next = if (editing == null) {
                prompts + saved
            } else {
                prompts.map { if (it.id == saved.id) saved else it }
            }
            prompts = next
            store.postProcessPrompts = next.map { it.text }
            editing = null
        },
        onDismiss = {
            editorVisible = false
            editing = null
        },
    )
}

private fun allDefaults(): Set<String> =
    PostProcessProvider.entries.map { it.defaultBaseUrl }.toSet()
