package com.handy.app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
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

    private fun buildSettings() = AppSettings(
        idleTimeout = _uiState.value.idleTimeout,
        shizukuEnabled = _uiState.value.shizukuEnabled,
        postProcessEndpoint = _uiState.value.postProcessEndpoint,
        postProcessApiKey = _uiState.value.postProcessApiKey,
        batteryOptimizationExempt = _uiState.value.batteryOptimizationExempt,
    )
}
