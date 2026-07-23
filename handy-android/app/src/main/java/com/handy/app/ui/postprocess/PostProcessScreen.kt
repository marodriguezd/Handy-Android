package com.handy.app.ui.postprocess

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.postprocess.Prompt as RepoPrompt
import com.handy.app.postprocess.PromptsRepository
import com.handy.app.ui.components.SettingsGroup
import com.handy.app.ui.components.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val promptsRepo = remember { PromptsRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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
    var prompts by remember { mutableStateOf(promptsRepo.getPrompts().toUiList()) }
    var activePromptId by remember { mutableStateOf(promptsRepo.getActivePrompt().id) }
    var editorVisible by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PostProcessPrompt?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(promptsRepo.exportToJson().toByteArray())
                }
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.postprocess_prompt_export_success))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.postprocess_prompt_export_error))
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: ""
                val success = promptsRepo.importFromJson(json)
                val loadedPrompts = promptsRepo.getPrompts().toUiList()
                val active = promptsRepo.getActivePrompt().id
                withContext(Dispatchers.Main) {
                    prompts = loadedPrompts
                    activePromptId = active
                    val messageRes = if (success) R.string.postprocess_prompt_import_success else R.string.postprocess_prompt_import_error
                    snackbarHostState.showSnackbar(context.getString(messageRes))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    snackbarHostState.showSnackbar(context.getString(R.string.postprocess_prompt_import_error))
                }
            }
        }
    }

    LaunchedEffect(provider) {
        if (baseUrl.isBlank()) {
            baseUrl = provider.defaultBaseUrl
            store.postProcessEndpoint = baseUrl
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.lg,
                bottom = Spacing.lg,
            ),
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
                activePromptId = activePromptId,
                onAdd = {
                    editing = null
                    editorVisible = true
                },
                onEdit = {
                    editing = it
                    editorVisible = true
                },
                onDelete = { prompt ->
                    promptsRepo.deletePrompt(prompt.id)
                    prompts = promptsRepo.getPrompts().toUiList()
                    if (activePromptId == prompt.id) {
                        activePromptId = promptsRepo.getActivePrompt().id
                    }
                },
                onActivate = { prompt ->
                    promptsRepo.setActivePromptId(prompt.id)
                    activePromptId = prompt.id
                },
                onExport = { exportLauncher.launch("handy_prompts.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
            )
        }
    }
}

    PromptEditor(
        visible = editorVisible,
        initial = editing,
        onSave = { saved ->
            promptsRepo.savePrompt(saved.toRepo())
            prompts = promptsRepo.getPrompts().toUiList()
            if (activePromptId == RepoPrompt.BUILTIN_ID) {
                activePromptId = promptsRepo.getActivePrompt().id
            }
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

private fun RepoPrompt.toUi(): PostProcessPrompt = PostProcessPrompt(id, name, body)

private fun PostProcessPrompt.toRepo(): RepoPrompt = RepoPrompt(id, name, text)

private fun List<RepoPrompt>.toUiList(): List<PostProcessPrompt> = map { it.toUi() }
