package com.handy.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.SettingsStore
import com.handy.app.model.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val settingsStore: SettingsStore,
    private val modelsViewModelFactory: () -> ModelsViewModel,
) : ViewModel() {

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

    fun nextStep() {
        val next = _uiState.value.currentStep + 1
        if (next < _uiState.value.totalSteps) {
            _uiState.update { it.copy(currentStep = next) }
            if (next == 3) {
                initModelDownload()
            }
        }
    }

    fun previousStep() {
        val prev = _uiState.value.currentStep - 1
        if (prev >= 0) {
            _uiState.update { it.copy(currentStep = prev) }
        }
    }

    fun setMicrophonePermission(granted: Boolean) {
        _uiState.update { it.copy(hasMicrophonePermission = granted) }
    }

    fun skipToModelDownload() {
        _uiState.update { it.copy(currentStep = 3) }
        initModelDownload()
    }

    fun completeOnboarding() {
        settingsStore.onboardingCompleted = true
    }

    fun skipDownload() {
        // Also cancel the actual download on the Rust side so it stops
        // downloading and the tokio task can clean up and fire the
        // completion callback, which updates the ModelsViewModel UI.
        com.handy.app.bridge.EngineBridge.nativeCancelDownload()
        downloadStarted = false
        skipped = true
        retryingDownload = false
        activated = false
        _uiState.update {
            it.copy(isDownloadCanceled = true, isDownloadReady = false, isDownloading = false)
        }
    }

    fun retryDownload() {
        // Reset state so initModelDownload can start a fresh download
        downloadStarted = false
        skipped = false
        retryingDownload = true
        activated = false
        _uiState.update {
            it.copy(isDownloadCanceled = false, isDownloadReady = false, isDownloading = false)
        }
        initModelDownload()
    }

    private fun initModelDownload() {
        if (modelsViewModel == null) {
            modelsViewModel = modelsViewModelFactory()
            val mv = modelsViewModel!!
            viewModelScope.launch {
                mv.uiState.collect { modelState ->
                    // Scan ALL downloads in the map for completion events
                    val completedEvent = modelState.downloads.entries
                        .firstOrNull { (_, e) -> e.isComplete }
                        ?.let { (id, event) -> id to event }

                    if (completedEvent != null) {
                        val (id, event) = completedEvent
                        if (event.error != null) {
                            // Download failed — show error
                            _uiState.update {
                                it.copy(
                                    downloadProgress = 0f,
                                    downloadError = event.error,
                                    isDownloadReady = false,
                                    isDownloading = false,
                                )
                            }
                        } else {
                            // Download succeeded
                            if (!activated) {
                                activated = true
                                mv.setActiveModel(id)
                            }
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
                        val target = models.firstOrNull { it.recommended && !it.isDownloaded }
                            ?: models.firstOrNull { !it.isDownloaded }
                        val existing = target?.let { modelState.downloads[it.id] }
                        // Start download if: no prior entry exists, OR the prior entry completed (success or failure)
                        if (target != null && (existing == null || existing.isComplete)) {
                            downloadStarted = true
                            downloadTargetId = target.id
                            _uiState.update { it.copy(isDownloading = true) }
                            mv.downloadModel(target.id)
                        } else if (models.all { it.isDownloaded }) {
                            _uiState.update { it.copy(isDownloadReady = true) }
                            val id = downloadTargetId ?: models.firstOrNull { it.isDownloaded }?.id
                            if (id != null && !activated) {
                                activated = true
                                mv.setActiveModel(id)
                            }
                        }
                    }
                }
            }
        }
        // (Re)load models — triggers the collector above which picks up a fresh download
        modelsViewModel!!.loadModels()
    }
}
