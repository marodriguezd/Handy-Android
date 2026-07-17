package com.handy.app.ui.postprocess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
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
 * Sprint 26 + Sprint 28c — Top-level Post-Process destination composable.
 *
 * Sprint 28c migration: outer body migrated from
 * `Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()))`
 * to `LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = ...,
 * verticalArrangement = Arrangement.spacedBy(Spacing.lg))`. Each previous
 * Column child becomes an `item { ... }` block; inter-item gap is supplied
 * by the LazyColumn's `verticalArrangement` (no more manual Spacer elements
 * between groups).
 *
 * Why this migration matters — fixes the on-device crash:
 *
 * Compose's `verticalScroll` modifier on a Column trips the runtime check
 * `IllegalStateException: Vertically scrollable component was measured with
 * an infinity maximum height constraints, which is disallowed.` whenever
 * its parent supplies `Constraints.Infinity` for `maxHeight`. Compose
 * Navigation's `NavHost` wraps each destination in `AnimatedContent`,
 * which during transitions can supply Infinity to the destination body.
 * Pre-Sprint 28c, tapping the Post-Process nav tile therefore crashed on
 * A059 Android 16.
 *
 * `LazyColumn`, by contrast, accepts Infinity bounds gracefully (its measure
 * pass only sees visible items in the viewport, never the intrinsic content
 * height). Migrating PostProcessScreen's outer column to LazyColumn closes
 * the crash class for this destination. Companion change in `MainActivity.kt`:
 * the redundant `Column.verticalScroll(...)` wrapper around the
 * `postProcessContent` lambda was REMOVED — LazyColumn now owns the scroll
 * surface, matching HistoryScreen / ModelCatalogScreen.
 *
 * Reads from `SettingsStore`:
 *   - `settingsStore.postProcessProviderId`
 *   - `settingsStore.postProcessEndpoint`
 *   - `settingsStore.postProcessApiKey`
 *   - `settingsStore.postProcessModel`
 *   - `settingsStore.postProcessPrompts` (Sprint 26)
 *
 * Provider changes auto-fill the base URL when the user hasn't manually
 * overridden it (we detect "untouched" by comparing the current value against
 * the union of all known `defaultBaseUrl` values).
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

    LaunchedEffect(provider) {
        if (baseUrl.isBlank()) {
            baseUrl = provider.defaultBaseUrl
            store.postProcessEndpoint = baseUrl
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        item {
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
        }
        item {
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
        }
        item {
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
