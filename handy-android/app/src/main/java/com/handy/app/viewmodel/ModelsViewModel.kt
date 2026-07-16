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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelsViewModel(
    private val app: HandyApplication,
    private val engineViewModel: EngineViewModel = app.engineViewModel,
) : ViewModel() {

    data class UiState(
        /** Raw list of models as returned by JNI (no annotations). */
        val models: List<ModelInfo> = emptyList(),
        /** Capability-annotated, sorted, filtered list for the catalog UI. */
        val visibleModels: List<Pair<ModelInfo, ModelCompatibility>> = emptyList(),
        /** Device capability snapshot (null until first detect() completes). */
        val snapshot: CapabilitySnapshot? = null,
        val isLoading: Boolean = false,
        val activeDownloadId: String? = null,
        val downloads: Map<String, EngineViewModel.DownloadProgressEvent> = emptyMap(),
        val showExperimental: Boolean = false,
        val showLargeModelDialogFor: ModelInfo? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Eager snapshot + restored preference
        val snap = DeviceCapabilityDetector.detect(app)
        val showExp = app.settingsStore.showExperimentalModels
        _uiState.update {
            it.copy(
                snapshot = snap,
                showExperimental = showExp,
                visibleModels = computeVisibleList(emptyList(), snap, showExp),
            )
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
                _uiState.update {
                    it.copy(
                        models = raw,
                        isLoading = false,
                        visibleModels = computeVisibleList(raw, it.snapshot, it.showExperimental),
                    )
                }
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
        _uiState.update {
            it.copy(
                showExperimental = show,
                visibleModels = computeVisibleList(it.models, it.snapshot, show),
            )
        }
    }

    /** Re-reads memory info from the OS. */
    fun refreshCapability() {
        val snap = DeviceCapabilityDetector.detect(app)
        _uiState.update {
            it.copy(
                snapshot = snap,
                visibleModels = computeVisibleList(it.models, snap, it.showExperimental),
            )
        }
    }

    /**
     * Triggers a download unless the model is gated. If the model is heavy
     * / extreme, surfaces the consent dialog instead.
     */
    fun attemptDownload(model: ModelInfo) {
        val compat = compatFor(model) ?: return
        if (compat.requiresConsent) {
            _uiState.update { it.copy(showLargeModelDialogFor = model) }
        } else {
            actuallyDownload(model.id)
        }
    }

    /**
     * Imperative (non-gating) download. Used by flows like onboarding that
     * run an autonomous step and can't interrupt the user with a consent
     * dialog. Catalog UI must use [attemptDownload] instead.
     */
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

    /**
     * Annotate + filter hidden + sort by status / mobile-promotion /
     * recommended / size. Stable contract: experimental models with
     * `hidden=true` are excluded.
     *
     * Promotion bucket (per [MobileRecommendationsFile.promotionBucket]):
     *   0 = tier-primary   (curated primary for this DeviceTier)
     *   1 = tier-alternative (curated alternatives for this DeviceTier)
     *   2 = not promoted
     */
    private fun computeVisibleList(
        raw: List<ModelInfo>,
        snapshot: CapabilitySnapshot?,
        showExp: Boolean,
    ): List<Pair<ModelInfo, ModelCompatibility>> {
        if (raw.isEmpty()) return emptyList()
        val snap = snapshot ?: snapshotOrFallback()
        val recs = MobileRecommendations.load(app)
        return computeVisibleCatalog(raw, snap, recs, showExp)
    }

    private fun snapshotOrFallback(): CapabilitySnapshot =
        _uiState.value.snapshot ?: DeviceCapabilityDetector.detect(app)
}
