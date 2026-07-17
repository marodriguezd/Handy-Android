package com.handy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.handy.app.bridge.EngineBridge
import com.handy.app.model.HistoryEntry
import com.handy.app.model.ModelInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manifest-declared receiver used only during automated ADB tests.
 * Allows downloading and activating a model by ID without relying on UI taps,
 * and seeds synthetic History entries so the visual diff of the MD3
 * HistoryScreen can render cards without a real recording.
 *
 * Security measures:
 * - Manifest requires [android.permission.DUMP], which is held by the ADB shell user.
 * - Receiver is disabled in release builds via manifest placeholder.
 * - model_id is validated against the catalog before any native call.
 * - Runtime check ensures the receiver only runs in debug builds.
 * - Native operations are serialized with process-wide mutexes.
 *
 * Pre-Sprint-26 Batch D (2026-07-17):
 * - Added `com.handy.app.action.SEED_HISTORY` broadcast that injects
 *   `count` synthetic entries (default 5, max 50) into
 *   [syntheticHistoryEntries]. [com.handy.app.viewmodel.HistoryViewModel]
 *   splices these entries above native ones on the first page-load.
 *   Debug-only via the existing `BuildConfig.DEBUG` gate.
 */
class TestCommandReceiver : BroadcastReceiver() {

    companion object {
        private val downloadMutex = Mutex()
        private val setActiveMutex = Mutex()
        private val cacheMutex = Mutex()

        /** Allowed characters for a model / download ID. */
        private val DOWNLOAD_ID_PATTERN = Regex("""^[A-Za-z0-9_\-]+$""")

        /**
         * Cache of valid catalog model IDs to avoid repeated JNI work.
         * The catalog is treated as static for the lifetime of the process.
         */
        private var validModelIds: Set<String>? = null

        /** Max seed count per SEED_HISTORY broadcast — keeps a runaway
         *  ADB script from injecting 10000 entries and OOMing the VM. */
        private const val MAX_SEED_HISTORY_COUNT = 50

        /**
         * Synthetic history entries injected via SEED_HISTORY broadcast.
         * [com.handy.app.viewmodel.HistoryViewModel] reads this on the
         * first page load to render MD3 History cards without a real
         * recording having happened. Debug-only.
         */
        private val syntheticHistoryEntries = mutableListOf<HistoryEntry>()

        /** Snapshot for [com.handy.app.viewmodel.HistoryViewModel]. */
        @JvmStatic
        fun getSyntheticHistorySnapshot(): List<HistoryEntry> = syntheticHistoryEntries.toList()

        /** Test seam — clears any previously-seeded entries. */
        @JvmStatic
        fun clearSyntheticHistory() = syntheticHistoryEntries.clear()
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Defense-in-depth: this receiver should only be reachable in debug builds,
        // but make it a no-op in release just in case the manifest merge ever fails.
        if (!BuildConfig.DEBUG) {
            Log.w("HandyApp", "TestCommandReceiver: ignored in non-debug build")
            return
        }
        // Dispatch first by action, then validate the action's required
        // extras inside the handler. Pre-Sprint-26 Batch D added
        // SEED_HISTORY which doesn't carry a model_id, so the old
        // top-level extras check had to move down.
        when (intent.action) {
            "com.handy.app.action.DOWNLOAD_MODEL",
            "com.handy.app.action.SET_ACTIVE_MODEL" -> {
                val modelId = intent.getStringExtra("model_id")
                if (modelId.isNullOrBlank()) {
                    Log.w(
                        "HandyApp",
                        "TestCommandReceiver: missing model_id for ${intent.action}",
                    )
                    return
                }
                handleModelAction(context, intent, modelId)
            }
            "com.handy.app.action.SEED_HISTORY" -> handleSeedHistoryAction(intent)
            else -> Log.w(
                "HandyApp",
                "TestCommandReceiver: unknown action ${intent.action}",
            )
        }
    }

    private fun handleModelAction(context: Context, intent: Intent, modelId: String) {
        val pendingResult = goAsync()
        val app = context.applicationContext as HandyApplication
        app.runTestCommand {
            try {
                if (!isValidModel(modelId)) {
                    Log.e("HandyApp", "TestCommandReceiver: invalid model_id $modelId")
                    return@runTestCommand
                }
                when (intent.action) {
                    "com.handy.app.action.DOWNLOAD_MODEL" -> {
                        downloadMutex.withLock {
                            Log.i("HandyApp", "TestCommandReceiver: downloading model $modelId")
                            EngineBridge.nativeDownloadModel(modelId)
                            Log.i("HandyApp", "TestCommandReceiver: download initiated for $modelId")
                        }
                    }
                    "com.handy.app.action.SET_ACTIVE_MODEL" -> {
                        setActiveMutex.withLock {
                            Log.i("HandyApp", "TestCommandReceiver: setting active model $modelId")
                            EngineBridge.nativeSetActiveModel(modelId)
                            Log.i("HandyApp", "TestCommandReceiver: active model set to $modelId")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HandyApp", "TestCommandReceiver: operation failed for $modelId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * SEED_HISTORY handler — injects synthetic entries into the
     * process-static list consumed by
     * [com.handy.app.viewmodel.HistoryViewModel.loadMore]. Capped at
     * [MAX_SEED_HISTORY_COUNT] to keep a runaway ADB script from
     * accidentally OOMing the process.
     *
     * Synchronization: [syntheticHistoryEntries] mutations happen only
     * from inside `BroadcastReceiver.onReceive`, which is single-
     * threaded per intent on Android. We therefore do NOT need
     * `kotlinx.coroutines.sync.Mutex` here (suspending `withLock` is
     * not callable from a non-suspend context). The
     * `MutableList<HistoryEntry>` itself is a process-singleton and
     * safe to mutate directly from `onReceive` callers.
     *
     * Recognized extras:
     *   - `count` (int): number of synthetic entries to seed. Default 5.
     */
    private fun handleSeedHistoryAction(intent: Intent) {
        val rawCount = intent.getIntExtra("count", 5)
        val count = rawCount.coerceIn(0, MAX_SEED_HISTORY_COUNT)
        if (count == 0) {
            clearSyntheticHistory()
            Log.i("HandyApp", "TestCommandReceiver: cleared synthetic history (count=0)")
            return
        }
        val seeds = (1..count).map { i ->
            HistoryEntry(
                id = (100_000 + i).toLong(),
                text = "Synthetic entry #$i: dictation sample $i",
                postProcessedText = if (i % 2 == 0) "[post-processed] dictation $i" else null,
                timestamp = System.currentTimeMillis() - i * 60_000L,
                isSaved = i % 3 == 0,
                audioPath = null,
            )
        }
        syntheticHistoryEntries.clear()
        syntheticHistoryEntries.addAll(seeds)
        Log.i(
            "HandyApp",
            "TestCommandReceiver: seeded ${seeds.size} synthetic history entries (requested=$rawCount)",
        )
    }

    private suspend fun isValidModel(modelId: String): Boolean {
        if (modelId.isBlank()) return false

        // Reject anything that could be used for path traversal or injection.
        if (!DOWNLOAD_ID_PATTERN.matches(modelId)) {
            Log.w("HandyApp", "TestCommandReceiver: model_id contains illegal characters: $modelId")
            return false
        }

        cacheMutex.withLock {
            val cached = validModelIds
            if (cached != null) {
                return cached.contains(modelId)
            }
        }

        return try {
            val catalogJson = EngineBridge.nativeGetAvailableModels()
            val catalog = ModelInfo.fromJsonArray(catalogJson)
            val ids = catalog.map { it.id }.toSet()
            cacheMutex.withLock {
                validModelIds = ids
            }
            ids.contains(modelId)
        } catch (e: Exception) {
            Log.e("HandyApp", "TestCommandReceiver: failed to validate model_id", e)
            false
        }
    }
}
