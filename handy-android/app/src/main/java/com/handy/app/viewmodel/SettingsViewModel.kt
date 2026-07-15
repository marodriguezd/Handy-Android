package com.handy.app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.SettingsStore
import com.handy.app.model.AppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val app: Application,
    private val settingsStore: SettingsStore,
    private val engineViewModel: EngineViewModel,
) : ViewModel() {

    private var debounceJob: Job? = null

    data class UiState(
        val idleTimeout: Int = 30,
        val shizukuEnabled: Boolean = false,
        val postProcessEndpoint: String = "",
        val postProcessApiKey: String = "",
        val isApiKeyVisible: Boolean = false,
        val batteryOptimizationExempt: Boolean = false,
        val experimentalEnabled: Boolean = false,
        val vadEnabled: Boolean = true,
        val addFinalSpace: Boolean = false,
        val postProcessingEnabled: Boolean = true,
        val autoSend: String = "disabled",
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        _uiState.value = UiState(
            idleTimeout = settingsStore.idleTimeout,
            shizukuEnabled = settingsStore.shizukuEnabled,
            postProcessEndpoint = settingsStore.postProcessEndpoint,
            postProcessApiKey = settingsStore.postProcessApiKey,
            batteryOptimizationExempt = settingsStore.batteryOptimizationExempt,
            experimentalEnabled = settingsStore.experimentalEnabled,
            vadEnabled = settingsStore.vadEnabled,
            addFinalSpace = settingsStore.addFinalSpace,
            postProcessingEnabled = settingsStore.postProcessingEnabled,
            autoSend = settingsStore.autoSend,
        )
    }

    fun setIdleTimeout(seconds: Int) {
        settingsStore.idleTimeout = seconds
        _uiState.update { it.copy(idleTimeout = seconds) }
        engineViewModel.applySettings(buildSettings())
    }

    fun setShizukuEnabled(enabled: Boolean) {
        settingsStore.shizukuEnabled = enabled
        _uiState.update { it.copy(shizukuEnabled = enabled) }
    }

    fun setPostProcessEndpoint(endpoint: String) {
        settingsStore.postProcessEndpoint = endpoint
        _uiState.update { it.copy(postProcessEndpoint = endpoint) }
        debounceApplySettings()
    }

    fun setPostProcessApiKey(apiKey: String) {
        settingsStore.postProcessApiKey = apiKey
        _uiState.update { it.copy(postProcessApiKey = apiKey) }
        debounceApplySettings()
    }

    private fun debounceApplySettings() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(500)
            engineViewModel.applySettings(buildSettings())
        }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
    }

    fun setBatteryOptimizationExempt(exempt: Boolean) {
        settingsStore.batteryOptimizationExempt = exempt
        _uiState.update { it.copy(batteryOptimizationExempt = exempt) }
    }

    fun setExperimentalEnabled(enabled: Boolean) {
        settingsStore.experimentalEnabled = enabled
        _uiState.update { it.copy(experimentalEnabled = enabled) }
    }

    fun setVadEnabled(enabled: Boolean) {
        settingsStore.vadEnabled = enabled
        _uiState.update { it.copy(vadEnabled = enabled) }
    }

    fun setAddFinalSpace(enabled: Boolean) {
        settingsStore.addFinalSpace = enabled
        _uiState.update { it.copy(addFinalSpace = enabled) }
    }

    fun setPostProcessingEnabled(enabled: Boolean) {
        settingsStore.postProcessingEnabled = enabled
        _uiState.update { it.copy(postProcessingEnabled = enabled) }
        debounceApplySettings()
    }

    fun setAutoSend(value: String) {
        settingsStore.autoSend = value
        _uiState.update { it.copy(autoSend = value) }
    }

    private fun buildSettings() = AppSettings(
        idleTimeout = _uiState.value.idleTimeout,
        shizukuEnabled = _uiState.value.shizukuEnabled,
        postProcessEndpoint = _uiState.value.postProcessEndpoint,
        postProcessApiKey = _uiState.value.postProcessApiKey,
        batteryOptimizationExempt = _uiState.value.batteryOptimizationExempt,
        experimentalEnabled = _uiState.value.experimentalEnabled,
        vadEnabled = _uiState.value.vadEnabled,
        addFinalSpace = _uiState.value.addFinalSpace,
        postProcessingEnabled = _uiState.value.postProcessingEnabled,
        autoSend = _uiState.value.autoSend,
    )
}
