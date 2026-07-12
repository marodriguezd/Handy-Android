package com.handy.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.SettingsStore
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
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var modelsViewModel: ModelsViewModel? = null

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

    private fun initModelDownload() {
        if (modelsViewModel != null) return
        modelsViewModel = modelsViewModelFactory()
        viewModelScope.launch {
            modelsViewModel!!.uiState.collect { modelState ->
                val activeId = modelState.activeDownloadId
                val event = if (activeId != null) modelState.downloads[activeId] else null
                _uiState.update {
                    it.copy(
                        downloadProgress = event?.progress ?: 0f,
                        downloadError = if (event?.isComplete == true) event.error else null,
                        isDownloadReady = event?.isComplete == true && event.error == null,
                    )
                }
            }
        }
        modelsViewModel!!.loadModels()
    }
}
