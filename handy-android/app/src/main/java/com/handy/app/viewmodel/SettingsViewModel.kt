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
        engineViewModel.applySettings(_uiState.value.toAppSettings())
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
            engineViewModel.applySettings(_uiState.value.toAppSettings())
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

}

/**
 * Pure mapping from [SettingsViewModel.UiState] to [AppSettings] consumed by
 * the engine. Tests for this extension live in
 * `app/src/test/java/com/handy/app/viewmodel/SettingsViewModelUiStateTest.kt`.
 *
 * `isApiKeyVisible` is intentionally NOT mapped — it is a pure UI flag with
 * no engine counterpart, and pre-Sprint-26 Batch B explicitly verified that
 * omission in CI.
 *
 * Marked `internal` to keep the mapping scoped to the `app` Gradle module
 * (the test source-set in the same module can still access it for coverage).
 */
internal fun SettingsViewModel.UiState.toAppSettings(): AppSettings = AppSettings(
    idleTimeout = idleTimeout,
    shizukuEnabled = shizukuEnabled,
    postProcessEndpoint = postProcessEndpoint,
    postProcessApiKey = postProcessApiKey,
    batteryOptimizationExempt = batteryOptimizationExempt,
    experimentalEnabled = experimentalEnabled,
    vadEnabled = vadEnabled,
    addFinalSpace = addFinalSpace,
    postProcessingEnabled = postProcessingEnabled,
    autoSend = autoSend,
)
