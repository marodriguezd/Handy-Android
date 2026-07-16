package com.handy.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.HandyApplication
import com.handy.app.bridge.EngineBridge
import com.handy.app.capability.CapabilitySnapshot
import com.handy.app.capability.DeviceCapabilityDetector
import com.handy.app.capability.MobileRecommendations
import com.handy.app.capability.ModelCompatibility
import com.handy.app.capability.computeCompatibility
import com.handy.app.capability.computeVisibleCatalog
import com.handy.app.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelsViewModel(
    private val app: HandyApplication,
    private val engineViewModel: EngineViewModel = app.engineViewModel,
) : ViewModel() {

    data class UiState(
        val models: List<ModelInfo> = emptyList(),
        val visibleModels: List<Pair<ModelInfo, ModelCompatibility>> = emptyList(),
        val snapshot: CapabilitySnapshot? = null,
        val isLoading: Boolean = false,
        val activeDownloadId: String? = null,
        val downloads: Map<String, EngineViewModel.DownloadProgressEvent> = emptyMap(),
        val showExperimental: Boolean = false,
        val showLargeModelDialogFor: ModelInfo? = null,
        // ── Sprint 20 — Catalog filters (single source of truth) ──
        val searchQuery: String = "",
        val languageFilter: String? = null,
        val onlyRecommended: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val snap = DeviceCapabilityDetector.detect(app)
        val showExp = app.settingsStore.showExperimentalModels
        _uiState.update {
            it.copy(snapshot = snap, showExperimental = showExp)
        }

        viewModelScope.launch {
            engineViewModel.downloadEvents.collect { event ->
                _uiState.update { state ->
                    state.copy(
                        downloads = state.downloads + (event.modelId to event),
                        activeDownloadId = if (!event.isComplete) event.modelId else null,
                    )
                }
            }
        }
        viewModelScope.launch {
            engineViewModel.availableModels.collect { raw ->
                _uiState.update { it.copy(models = raw, isLoading = false) }
            }
        }

        // Catalog pipeline — derived purely from `_uiState` so every
        // (snapshot, filter, raw) change triggers exactly one recomputation.
        // Wrapping each input in `distinctUntilChanged()` prevents feedback
        // loops when our own emission replaces the same value with itself.
        viewModelScope.launch {
            val snapshotAndShowExp = combine(
                _uiState.map { it.snapshot }.distinctUntilChanged(),
                _uiState.map { it.showExperimental }.distinctUntilChanged(),
            ) { snapOuter, showExpOuter -> snapOuter to showExpOuter }

            combine(
                _uiState.map { it.searchQuery }.distinctUntilChanged(),
                _uiState.map { it.languageFilter }.distinctUntilChanged(),
                _uiState.map { it.onlyRecommended }.distinctUntilChanged(),
                engineViewModel.availableModels,
                snapshotAndShowExp,
            ) { query, lang, recOnly, raw, snapShowExp ->
                val (snapSrc, showExpSrc) = snapShowExp
                if (snapSrc == null || raw.isEmpty()) {
                    return@combine emptyList<Pair<ModelInfo, ModelCompatibility>>()
                }
                val recs = MobileRecommendations.load(app)
                // Sprint 22: filters are evaluated inside `computeVisibleCatalog`
                // (pure, unit-tested). The 10 existing tests still pass because
                // the filter params default to "no filter".
                computeVisibleCatalog(raw, snapSrc, recs, showExpSrc, query, lang, recOnly)
            }.collect { sorted ->
                _uiState.update { it.copy(visibleModels = sorted) }
            }
        }
    }

    // ── Public actions ─────────────────────────────────────────

    fun loadModels() {
        _uiState.update { it.copy(isLoading = true) }
        engineViewModel.refreshModels()
    }

    fun setShowExperimental(show: Boolean) {
        app.settingsStore.showExperimentalModels = show
        _uiState.update { it.copy(showExperimental = show) }
    }

    fun refreshCapability() {
        val snap = DeviceCapabilityDetector.detect(app)
        _uiState.update { it.copy(snapshot = snap) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setLanguageFilter(language: String?) {
        _uiState.update { it.copy(languageFilter = language) }
    }

    fun setOnlyRecommended(onlyRecommended: Boolean) {
        _uiState.update { it.copy(onlyRecommended = onlyRecommended) }
    }

    /**
     * Returns the top-N most-frequent language tags present in the raw
     * catalog.  Used by the catalog UI to build the language filter row.
     * The result is intentionally not cached here because callers
     * already key their `remember(...)` on `uiState.models`.
     */
    fun availableLanguages(limit: Int = 5): List<String> {
        return _uiState.value.models.asSequence()
            .flatMap { it.language.split(",").asSequence() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
            .toList()
    }

    fun attemptDownload(model: ModelInfo) {
        val compat = compatFor(model) ?: return
        if (compat.requiresConsent) {
            _uiState.update { it.copy(showLargeModelDialogFor = model) }
        } else {
            actuallyDownload(model.id)
        }
    }

    fun downloadModel(modelId: String) {
        actuallyDownload(modelId)
    }

    fun confirmLargeModelDownload(model: ModelInfo) {
        _uiState.update { it.copy(showLargeModelDialogFor = null) }
        actuallyDownload(model.id)
    }

    fun cancelLargeModelDownload() {
        _uiState.update { it.copy(showLargeModelDialogFor = null) }
    }

    fun cancelDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeCancelDownload()
        }
    }

    fun deleteModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeDeleteModel(modelId)
            engineViewModel.refreshModels()
        }
    }

    fun setActiveModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeSetActiveModel(modelId)
            engineViewModel.refreshModels()
        }
    }

    // ── Internals ──────────────────────────────────────────────

    private fun compatFor(model: ModelInfo): ModelCompatibility? {
        val snap = _uiState.value.snapshot ?: return null
        return computeCompatibility(model, snap, _uiState.value.showExperimental)
    }

    private fun actuallyDownload(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeDownloadModel(modelId)
        }
    }
}
