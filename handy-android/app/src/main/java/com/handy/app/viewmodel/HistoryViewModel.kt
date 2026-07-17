package com.handy.app.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.handy.app.bridge.EngineBridge
import com.handy.app.model.HistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sprint 24 — HistoryViewModel moved from `ViewModel` to
 * `AndroidViewModel(application)` so the new `copyText` action can
 * reach the system `ClipboardManager` without dragging the screen
 * layer into the VM contract.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HistoryVM"
        private const val PAGE_SIZE = 20

        /** Simulated retranscribe latency for the Sprint 24 retry stub.
         *  When the future [EngineBridge.nativeRetryHistoryEntry] JNI call
         *  lands, this constant loses its meaning and `retry` becomes a
         *  pass-through. */
        private const val RETRY_SIMULATED_DELAY_MS = 2_000L
    }

    data class UiState(
        val entries: List<HistoryEntry> = emptyList(),
        val isLoading: Boolean = false,
        val hasMore: Boolean = true,
        /** ID of the entry currently being re-transcribed, or null. Lets
         *  the History card render an inline `CircularProgressIndicator`
         *  on the Retry button without a separate loading Boolean. */
        val retryingId: Long? = null,
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
                },
            )
        }
    }

    /**
     * Re-transcribe a recorded history entry from its backing audio file.
     *
     * **Today's implementation is a stub** that mirrors the contract the
     * future Rust call ([EngineBridge.nativeRetryHistoryEntry], once it
     * lands in handy-core) will satisfy. We:
     *   1. Flip [UiState.retryingId] so the History card renders an inline
     *      spinner on the Retry button.
     *   2. Log the request + the audioPath we'll hand to the engine (so
     *      end-to-end observability via `adb logcat -d | grep HistoryVM`
     *      survives without a JNI call yet).
     *   3. Simulate the work with a 2-second delay.
     *   4. Clear [UiState.retryingId] and log completion.
     *
     * When the future JNI call lands, only the body of the launch{}
     * changes — the state-flag contract stays.
     */
    fun retry(entry: HistoryEntry) {
        _uiState.update { it.copy(retryingId = entry.id) }
        Log.d(TAG, "Retry requested for id=${entry.id}, audioPath=${entry.audioPath}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(RETRY_SIMULATED_DELAY_MS)
                // Future: replace delay() with EngineBridge.nativeRetryHistoryEntry(entry.id)
                // plus a reload of the entry's text via EngineBridge.nativeGetHistory(...).
                Log.d(TAG, "Retry completed (stub) for id=${entry.id}")
            } catch (t: Throwable) {
                // Preserve structured-concurrency cancellation. If the
                // VM gets cleared mid-retry, the catch below would
                // otherwise swallow the CancellationException and the
                // (already-cancelled) coroutine would still tick the
                // finally block to clear the state flag. Re-throwing
                // keeps the structured-concurrency contract intact and
                // lets viewModelScope.cancel() propagate normally.
                if (t is CancellationException) throw t
                // Surface the failure to logcat so e2e tests can detect it,
                // and let `finally` clear the spinner regardless. The user
                // gets a recovered Retry button; the failure reason lands
                // in logcat for follow-up triage.
                Log.e(TAG, "Retry failed for id=${entry.id}", t)
            } finally {
                _uiState.update { it.copy(retryingId = null) }
            }
        }
    }

    /**
     * Copy the entry's text to the device clipboard. When
     * [HistoryEntry.postProcessedText] is present, copy that (treated as
     * the "preferred" output because it represents the user's intended
     * final transcription); otherwise fall back to the raw text.
     *
     * Uses Android's [ClipboardManager] directly with a "Handy Dictation"
     * clip label so the IME Confirm-bar copy action shares the same
     * visible identity across the app.
     *
     * Failures are logged but never propagated to the UI — clipboard is
     * a best-effort affordance.
     */
    fun copyText(entry: HistoryEntry) {
        val text = entry.postProcessedText ?: entry.text
        val ctx = getApplication<Application>()
        try {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Handy Dictation", text))
        } catch (e: Exception) {
            Log.w(TAG, "copyText failed for id=${entry.id}", e)
        }
    }
}
