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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var modelsViewModel: ModelsViewModel? = null
    private var downloadStarted = false

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
        _uiState.update {
            it.copy(isDownloadReady = true, isDownloading = false)
        }
    }

    private fun initModelDownload() {
        if (modelsViewModel != null) return
        modelsViewModel = modelsViewModelFactory()
        viewModelScope.launch {
            modelsViewModel!!.uiState.collect { modelState ->
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
                if (!downloadStarted && modelState.models.isNotEmpty() && !modelState.isLoading) {
                    val models = modelState.models
                    val target = models.firstOrNull { it.recommended && !it.isDownloaded }
                        ?: models.firstOrNull { !it.isDownloaded }
                    if (target != null && !modelState.downloads.containsKey(target.id)) {
                        downloadStarted = true
                        _uiState.update { it.copy(isDownloading = true) }
                        modelsViewModel!!.downloadModel(target.id)
                    } else if (models.all { it.isDownloaded }) {
                        _uiState.update { it.copy(isDownloadReady = true) }
                    }
                }
            }
        }
        modelsViewModel!!.loadModels()
    }
}
