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
            // Pre-Sprint-26 Batch D: TestCommandReceiver.SEED_HISTORY
            // injects synthetic entries via a process-static list. We
            // splice them in here on the FIRST page only so the visual
            // diff of MD3 HistoryScreen can render cards without a real
            // recording. Subsequent pages are pure native pagination.
            val synthetic = com.handy.app.TestCommandReceiver.getSyntheticHistorySnapshot()
            val nativeEntries = HistoryEntry.fromJsonArray(json)
            val newEntries = if (synthetic.isNotEmpty() && state.entries.isEmpty()) {
                synthetic + nativeEntries
            } else {
                nativeEntries
            }
            _uiState.update {
                it.copy(
                    entries = it.entries + newEntries,
                    isLoading = false,
                    hasMore = nativeEntries.size == PAGE_SIZE,
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
     * Flow ([ui.retryingId] is set true on entry, always cleared on exit
     * via the `finally` block, even on cancellation):
     *   1. Set [UiState.retryingId] so the History card renders an inline
     *      spinner on the Retry button.
     *   2. Try [EngineBridge.nativeRetryHistoryEntry] (declared in handy-core).
     *      - On `true`: pick up the freshly-retried text by reloading
     *        the first page. Native side updates in place.
     *      - On `false`: Rust side doesn't yet support the call. Fall back
     *        to the simulated-delay stub so the spinner still ends (no
     *        indefinite hang) until Sprint 25+ lands the binding.
     *      - On `UnsatisfiedLinkError`: same fallback path. The symbol
     *        being absent is structurally equivalent to `false` and we
     *        treat both as "Rust not ready".
     *   3. Clear [UiState.retryingId] in `finally`. `CancellationException`
     *      is always re-thrown to keep structured-concurrency intact.
     */
    fun retry(entry: HistoryEntry) {
        _uiState.update { it.copy(retryingId = entry.id) }
        Log.d(TAG, "Retry requested for id=${entry.id}, audioPath=${entry.audioPath}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nativeResult = try {
                    EngineBridge.nativeRetryHistoryEntry(entry.id)
                } catch (e: UnsatisfiedLinkError) {
                    // Rust side doesn't yet implement the binding. Mirror
                    // `false` so the UX doesn't hang; the user still sees
                    // the spinner end after RETRY_SIMULATED_DELAY_MS.
                    Log.w(TAG, "nativeRetryHistoryEntry not in libhandy_core.so; falling back", e)
                    false
                }
                if (nativeResult) {
                    Log.d(TAG, "Retry completed via Rust for id=${entry.id}; merging refreshed entry")
                    val json = EngineBridge.nativeGetHistory(0, PAGE_SIZE)
                    val refreshed = HistoryEntry.fromJsonArray(json)
                    _uiState.update { state ->
                        state.copy(entries = state.entries.map { existing ->
                            // Surgical update: replace the matching entry's
                            // text/postProcessedText inline so other
                            // already-loaded pages remain in place.
                            val match = refreshed.firstOrNull { it.id == existing.id }
                            if (match != null) {
                                existing.copy(
                                    text = match.text,
                                    postProcessedText = match.postProcessedText,
                                )
                            } else {
                                existing
                            }
                        })
                    }
                } else {
                    // Fall back to the Sprint 24 simulated delay so the
                    // spinner lands predictably while Rust catches up.
                    delay(RETRY_SIMULATED_DELAY_MS)
                    Log.d(TAG, "Retry completed (fallback delay) for id=${entry.id}")
                }
            } catch (t: Throwable) {
                // Preserve structured-concurrency cancellation. If the
                // VM gets cleared mid-retry, the catch below would
                // otherwise swallow the CancellationException and the
                // (already-cancelled) coroutine would still tick the
                // finally block to clear the state flag. Re-throwing
                // keeps the structured-concurrency contract intact and
                // lets viewModelScope.cancel() propagate normally.
                if (t is CancellationException) throw t
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
