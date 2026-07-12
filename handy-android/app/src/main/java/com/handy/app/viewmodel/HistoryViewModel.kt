package com.handy.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.bridge.EngineBridge
import com.handy.app.model.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {

    companion object {
        private const val PAGE_SIZE = 20
    }

    data class UiState(
        val entries: List<HistoryEntry> = emptyList(),
        val isLoading: Boolean = false,
        val hasMore: Boolean = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadMore()
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val json = EngineBridge.nativeGetHistory(state.entries.size, PAGE_SIZE)
            val newEntries = HistoryEntry.fromJsonArray(json)
            _uiState.update {
                it.copy(
                    entries = it.entries + newEntries,
                    isLoading = false,
                    hasMore = newEntries.size == PAGE_SIZE,
                )
            }
        }
    }

    fun deleteEntry(entry: HistoryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeDeleteHistoryEntry(entry.id)
        }
        _uiState.update {
            it.copy(entries = it.entries.filter { e -> e.id != entry.id })
        }
    }

    fun toggleSaved(entry: HistoryEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            EngineBridge.nativeToggleHistorySaved(entry.id)
        }
        _uiState.update {
            it.copy(
                entries = it.entries.map { e ->
                    if (e.id == entry.id) e.copy(isSaved = !e.isSaved) else e
                }
            )
        }
    }
}
