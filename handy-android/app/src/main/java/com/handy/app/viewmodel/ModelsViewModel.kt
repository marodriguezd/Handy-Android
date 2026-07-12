package com.handy.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.bridge.EngineBridge
import com.handy.app.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ModelsViewModel(
    private val engineViewModel: EngineViewModel,
) : ViewModel() {

    data class UiState(
        val models: List<ModelInfo> = emptyList(),
        val isLoading: Boolean = false,
        val activeDownloadId: String? = null,
        val downloads: Map<String, EngineViewModel.DownloadProgressEvent> = emptyMap(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
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
            engineViewModel.availableModels.collect { models ->
                _uiState.update { it.copy(models = models, isLoading = false) }
            }
        }
    }

    fun loadModels() {
        _uiState.update { it.copy(isLoading = true) }
        engineViewModel.refreshModels()
    }

    fun downloadModel(modelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeDownloadModel(modelId)
        }
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
}
