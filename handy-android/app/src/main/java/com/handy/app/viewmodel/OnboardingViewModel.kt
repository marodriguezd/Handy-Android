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
        retryingDownload = false
        activated = false
        _uiState.update {
            it.copy(isDownloadCanceled = true, isDownloadReady = false, isDownloading = false)
        }
    }

    fun retryDownload() {
        // Reset state so initModelDownload can start a fresh download
        downloadStarted = false
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
                    val event = modelState.activeDownloadId?.let { modelState.downloads[it] }
                    if (event != null) {
                        _uiState.update {
                            it.copy(
                                downloadProgress = event.progress,
                                downloadError = if (event.isComplete) event.error else null,
                                isDownloadReady = event.isComplete && event.error == null,
                                isDownloading = !event.isComplete,
                            )
                        }
                    }

                    // Find any completed download in the map (even if activeDownloadId was consumed)
                    val completedId = modelState.downloads.entries
                        .firstOrNull { (_, e) -> e.isComplete && e.error == null }
                        ?.key
                    if (completedId != null && !activated) {
                        activated = true
                        _uiState.update { it.copy(isDownloadReady = true, isDownloading = false) }
                        mv.setActiveModel(completedId)
                    }

                if (!downloadStarted && modelState.models.isNotEmpty() && !modelState.isLoading) {
                    val models = modelState.models
                    val target = models.firstOrNull { it.recommended && !it.isDownloaded }
                        ?: models.firstOrNull { !it.isDownloaded }
                    // Allow retry: accept if no entry exists, or if previous download completed (even with error)
                    val existing = target?.let { modelState.downloads[it.id] }
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
