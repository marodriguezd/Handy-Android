package com.handy.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.SettingsStore
import com.handy.app.capability.CompatibilityStatus
import com.handy.app.capability.DeviceCapabilityDetector
import com.handy.app.capability.ModelCapability
import com.handy.app.capability.computeCompatibility
import com.handy.app.model.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val settingsStore: SettingsStore,
    private val modelsViewModelFactory: () -> ModelsViewModel,
    private val app: Application? = null,
) : ViewModel() {

    companion object {
        private const val TAG = "OnboardingVM"
    }

    data class UiState(
        val currentStep: Int = 0,
        val totalSteps: Int = 5,
        val hasMicrophonePermission: Boolean = false,
        val downloadProgress: Float = 0f,
        val downloadError: String? = null,
        val isDownloadReady: Boolean = false,
        val isDownloading: Boolean = false,
        val isDownloadCanceled: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var modelsViewModel: ModelsViewModel? = null
    private var initialized = false
    private var downloadStarted = false
    private var downloadTargetId: String? = null
    private var activated = false
    private var skipped = false
    private var retryingDownload = false

    /** De-dup sentinel for failure logs (separate from downloadTargetId to avoid collision). */
    private var lastLoggedFailureId: String? = null

    /** Cached once at construction to avoid re-detecting device RAM on every state emission. */
    private val cachedSnapshot: com.handy.app.capability.CapabilitySnapshot? by lazy {
        app?.let { DeviceCapabilityDetector.detect(it) }
    }

    fun nextStep() {
        val cur = _uiState.value.currentStep
        val next = cur + 1
        if (next < _uiState.value.totalSteps) {
            _uiState.update { it.copy(currentStep = next) }
            Log.d(TAG, "nextStep: $cur -> $next")
            if (next == 3) {
                initModelDownload()
            }
        }
    }

    fun previousStep() {
        val cur = _uiState.value.currentStep
        val prev = cur - 1
        if (prev >= 0) {
            _uiState.update { it.copy(currentStep = prev) }
            Log.d(TAG, "previousStep: $cur -> $prev")
        }
    }

    fun setMicrophonePermission(granted: Boolean) {
        _uiState.update { it.copy(hasMicrophonePermission = granted) }
        Log.d(TAG, "setMicrophonePermission=$granted")
    }

    fun skipToModelDownload() {
        _uiState.update { it.copy(currentStep = 3) }
        Log.d(TAG, "skipToModelDownload: jumped to step 3")
        initModelDownload()
    }

    fun completeOnboarding() {
        settingsStore.onboardingCompleted = true
        Log.d(TAG, "completeOnboarding: onboardingCompleted=true")
    }

    fun skipDownload() {
        Log.d(TAG, "skipDownload: cancelling Rust download (target=$downloadTargetId)")
        // Also cancel the actual download on the Rust side so it stops
        // downloading and the tokio task can clean up and fire the
        // completion callback, which updates the ModelsViewModel UI.
        com.handy.app.bridge.EngineBridge.nativeCancelDownload()
        downloadStarted = false
        skipped = true
        retryingDownload = false
        activated = false
        lastLoggedFailureId = null
        _uiState.update {
            it.copy(isDownloadCanceled = true, isDownloadReady = false, isDownloading = false)
        }
    }

    fun retryDownload() {
        Log.d(TAG, "retryDownload: resetting state and re-initializing")
        // Reset state so initModelDownload can start a fresh download
        downloadStarted = false
        skipped = false
        retryingDownload = true
        activated = false
        lastLoggedFailureId = null
        _uiState.update {
            it.copy(isDownloadCanceled = false, isDownloadReady = false, isDownloading = false)
        }
        initModelDownload()
    }

    private fun initModelDownload() {
        if (modelsViewModel == null) {
            modelsViewModel = modelsViewModelFactory()
            val mv = modelsViewModel!!
            Log.d(TAG, "initModelDownload: created ModelsViewModel; snapshot=${cachedSnapshot?.toTier()}; showExperimental=${settingsStore.showExperimentalModels}")
            viewModelScope.launch {
                mv.uiState.collect { modelState ->
                    // Scan ALL downloads in the map for completion events
                    val completedEvent = modelState.downloads.entries
                        .firstOrNull { (_, e) -> e.isComplete }
                        ?.let { (id, event) -> id to event }

                    if (completedEvent != null) {
                        val (id, event) = completedEvent
                        if (event.error != null) {
                            // De-dupe: only log on first error for this id (separate from downloadTargetId
                            // because downloadTargetId is set BEFORE the download starts and would collide)
                            if (lastLoggedFailureId != id) {
                                Log.w(TAG, "Download failed: id=$id error=${event.error}")
                                lastLoggedFailureId = id
                            }
                            // Download failed — show error
                            _uiState.update {
                                it.copy(
                                    downloadProgress = 0f,
                                    downloadError = event.error,
                                    isDownloadReady = false,
                                    isDownloading = false,
                                )
                            }
                        } else if (!activated) {
                            Log.d(TAG, "Download complete: id=$id, activating model")
                            // Download succeeded
                            activated = true
                            mv.setActiveModel(id)
                            _uiState.update {
                                it.copy(
                                    downloadProgress = 1f,
                                    downloadError = null,
                                    isDownloadReady = true,
                                    isDownloading = false,
                                )
                            }
                        }
                    }

                    // Also update progress from active download
                    val progressEvent = modelState.activeDownloadId?.let { modelState.downloads[it] }
                    if (progressEvent != null && !progressEvent.isComplete) {
                        _uiState.update {
                            it.copy(
                                downloadProgress = progressEvent.progress,
                                downloadError = null,
                                isDownloadReady = false,
                                isDownloading = true,
                            )
                        }
                    }

                    // Auto-start download if models are loaded and no download has been started
                    if (!downloadStarted && !skipped && modelState.models.isNotEmpty() && !modelState.isLoading) {
                        val models = modelState.models
                        Log.d(TAG, "models loaded: ${models.size} entries; downloads=${modelState.downloads.size}; isLoading=${modelState.isLoading}")
                        // Tier-aware filter: pick a recommended model that fits the device AND
                        // does not require user consent (no Voxtral / no extreme). Falling back to
                        // the first model that fits and is safe, then any not-downloaded model.
                        val snapshot = cachedSnapshot
                        val showExp = settingsStore.showExperimentalModels
                        val compatForModel: (ModelInfo) -> Int = { m ->
                            if (snapshot != null) {
                                computeCompatibility(m, snapshot, showExp).status.ordinal
                            } else {
                                CompatibilityStatus.TIER_RECOMMENDED_DEEP.ordinal
                            }
                        }
                        val fitsAndSafe = { m: ModelInfo ->
                            val score = compatForModel(m)
                            score <= CompatibilityStatus.EXCEEDS.ordinal &&
                                !ModelCapability.isHeavyGate(m.id)
                        }
                        val target = models.firstOrNull { it.recommended && !it.isDownloaded && fitsAndSafe(it) }
                            ?: models.firstOrNull { !it.isDownloaded && fitsAndSafe(it) }
                        val existing = target?.let { modelState.downloads[it.id] }
                        // Start download if: no prior entry exists, OR the prior entry completed (success or failure)
                        if (target != null && (existing == null || existing.isComplete)) {
                            downloadStarted = true
                            downloadTargetId = target.id
                            Log.d(TAG, "Selected target: ${target.id} (size=${target.sizeBytes / (1024 * 1024)}MB, recommended=${target.recommended}, score=${compatForModel(target)})")
                            _uiState.update { it.copy(isDownloading = true) }
                            mv.downloadModel(target.id)
                        } else if (models.all { it.isDownloaded }) {
                            Log.d(TAG, "All models already downloaded; setting ready=true")
                            _uiState.update { it.copy(isDownloadReady = true) }
                            val id = downloadTargetId ?: models.firstOrNull { it.isDownloaded }?.id
                            if (id != null && !activated) {
                                activated = true
                                mv.setActiveModel(id)
                            }
                        } else if (target == null) {
                            // No safe target found for this device tier (e.g. LOW device with
                            // only heavy-gated models remaining). Defer to manual selection from
                            // the catalog screen where consent dialogs fire correctly.
                            Log.w(
                                TAG,
                                "No safe download target; deferring to manual selection (device tier may be too low).",
                            )
                            downloadStarted = true
                            _uiState.update {
                                it.copy(isDownloadReady = true, isDownloading = false, downloadError = null)
                            }
                        }
                    }
                }
            }
        } else {
            Log.d(TAG, "initModelDownload: ModelsViewModel already initialized; reloading models")
        }
        // (Re)load models — triggers the collector above which picks up a fresh download
        modelsViewModel!!.loadModels()
    }
}
