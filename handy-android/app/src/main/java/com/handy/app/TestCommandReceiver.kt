package com.handy.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.handy.app.bridge.EngineBridge
import com.handy.app.model.ModelInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manifest-declared receiver used only during automated ADB tests.
 * Allows downloading and activating a model by ID without relying on UI taps.
 *
 * Security measures:
 * - Manifest requires [android.permission.DUMP], which is held by the ADB shell user.
 * - Receiver is disabled in release builds via manifest placeholder.
 * - model_id is validated against the catalog before any native call.
 * - Runtime check ensures the receiver only runs in debug builds.
 * - Native operations are serialized with process-wide mutexes.
 */
class TestCommandReceiver : BroadcastReceiver() {

    companion object {
        private val downloadMutex = Mutex()
        private val setActiveMutex = Mutex()
        private val cacheMutex = Mutex()

        /** Allowed characters for a model / download ID. */
        private val DOWNLOAD_ID_PATTERN = Regex("""^[A-Za-z0-9_\\-]+$""")

        /**
         * Cache of valid catalog model IDs to avoid repeated JNI work.
         * The catalog is treated as static for the lifetime of the process.
         */
        private var validModelIds: Set<String>? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Defense-in-depth: this receiver should only be reachable in debug builds,
        // but make it a no-op in release just in case the manifest merge ever fails.
        if (!BuildConfig.DEBUG) {
            Log.w("HandyApp", "TestCommandReceiver: ignored in non-debug build")
            return
        }

        val modelId = intent.getStringExtra("model_id")
        if (modelId.isNullOrBlank()) {
            Log.w("HandyApp", "TestCommandReceiver: missing model_id")
            return
        }

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
                    else -> {
                        Log.w("HandyApp", "TestCommandReceiver: unknown action ${intent.action}")
                    }
                }
            } catch (e: Exception) {
                Log.e("HandyApp", "TestCommandReceiver: operation failed for $modelId", e)
            } finally {
                pendingResult.finish()
            }
        }
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
