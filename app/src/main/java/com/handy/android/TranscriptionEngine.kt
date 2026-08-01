package com.handy.android

import android.content.ComponentCallbacks2
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
import android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class NoModelException : IllegalStateException("No downloaded model is selected")
class UnsupportedModelException(fileName: String) :
    IllegalArgumentException("Unsupported Whisper model format: $fileName. Use a GGML .bin model.")

/** Keeps one native Whisper context alive for the currently selected model. */
internal class WhisperEngineCache {
    private var modelPath: String? = null
    private var engine: IWhisperEngine? = null

    @Synchronized
    fun get(modelPath: String, engineFactory: () -> IWhisperEngine): IWhisperEngine {
        val cachedEngine = engine
        if (cachedEngine != null && this.modelPath == modelPath) return cachedEngine

        cachedEngine?.cancelTranscribe()
        cachedEngine?.close()
        engine = null
        this.modelPath = null

        val nextEngine = engineFactory()
        try {
            check(nextEngine.init(modelPath)) { "Unable to initialize the selected model" }
        } catch (error: Throwable) {
            nextEngine.close()
            throw error
        }
        engine = nextEngine
        this.modelPath = modelPath
        return nextEngine
    }

    @Synchronized
    fun clear() {
        engine?.cancelTranscribe()
        engine?.close()
        engine = null
        modelPath = null
    }

    @Synchronized
    fun cachedModelPath(): String? = modelPath
}

object TranscriptionEngine {
    private val inferenceMutex = Mutex()
    private val cache = WhisperEngineCache()
    private val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateLock = Any()
    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit

        override fun onLowMemory() {
            requestMemoryEviction()
        }

        override fun onTrimMemory(level: Int) {
            if (level == TRIM_MEMORY_RUNNING_LOW || level == TRIM_MEMORY_RUNNING_MODERATE) {
                requestMemoryEviction()
            }
        }
    }

    private var registeredApplication: Context? = null
    private var activeEngine: IWhisperEngine? = null
    private var requestGeneration = 0L
    private var evictionRequested = false

    fun isSupportedModel(file: File): Boolean = file.isFile && file.extension.equals("bin", ignoreCase = true)

    fun isValidatedModel(file: File): Boolean =
        isSupportedModel(file) && ModelValidator.verifyRecordedDigest(file)

    suspend fun transcribe(
        context: Context,
        samples: FloatArray,
        engineFactory: () -> IWhisperEngine = { WhisperLib() },
    ): String {
        ensureComponentCallbacks(context)
        val requestId = beginRequest()

        return withContext(Dispatchers.Default) {
            inferenceMutex.withLock {
                if (!isLatest(requestId)) {
                    throw CancellationException("A newer transcription superseded this request")
                }
                evictIfRequested()
                val model = selectedModel(context) ?: throw NoModelException()
                if (!isValidatedModel(model)) {
                    throw ModelValidationException("Selected model has not passed integrity validation: ${model.name}")
                }

                val whisper = cache.get(model.absolutePath, engineFactory)
                synchronized(stateLock) {
                    if (requestId != requestGeneration) {
                        throw CancellationException("A newer transcription superseded this request")
                    }
                    activeEngine = whisper
                }
                try {
                    val result = whisper.transcribe(
                        audioData = samples,
                        numThreads = SettingsManager.threadCount(context)
                            ?: Runtime.getRuntime().availableProcessors().coerceAtMost(8),
                        translate = SettingsManager.translate(context),
                        language = SettingsManager.language(context),
                    ).trim()
                    val processedResult = PostProcessor.process(context, result)
                    if (!isLatest(requestId)) throw CancellationException("A newer transcription superseded this request")
                    processedResult
                } finally {
                    synchronized(stateLock) {
                        if (activeEngine === whisper) activeEngine = null
                    }
                }
            }
        }
    }

    fun selectedModel(context: Context): File? {
        val directory = File(context.filesDir, "models")
        val selected = SettingsManager.activeModelName(context)
            ?.let { File(directory, it) }
            ?.takeIf(::isValidatedModel)
        return selected ?: directory.listFiles()
            ?.sortedBy { it.name }
            ?.firstOrNull(::isValidatedModel)
    }

    private fun ensureComponentCallbacks(context: Context) {
        val application = context.applicationContext ?: context
        synchronized(stateLock) {
            if (registeredApplication === application) return
            registeredApplication?.unregisterComponentCallbacks(componentCallbacks)
            application.registerComponentCallbacks(componentCallbacks)
            registeredApplication = application
        }
    }

    private fun beginRequest(): Long {
        val (requestId, previous) = synchronized(stateLock) {
            requestGeneration += 1
            requestGeneration to activeEngine
        }
        previous?.cancelTranscribe()
        return requestId
    }

    private fun isLatest(requestId: Long): Boolean = synchronized(stateLock) { requestId == requestGeneration }

    private fun requestMemoryEviction() {
        val active = synchronized(stateLock) {
            evictionRequested = true
            activeEngine
        }
        active?.cancelTranscribe()
        lifecycleScope.launch { performPendingEviction() }
    }

    private suspend fun performPendingEviction() {
        inferenceMutex.withLock { evictIfRequested() }
    }

    private fun evictIfRequested() {
        val shouldEvict = synchronized(stateLock) { evictionRequested }
        if (!shouldEvict) return
        cache.clear()
        synchronized(stateLock) { evictionRequested = false }
    }

    /** Explicitly releases the cached native model, for app shutdown and tests. */
    fun release() {
        requestMemoryEviction()
    }

    internal fun cachedModelPathForTests(): String? = cache.cachedModelPath()

    internal suspend fun evictForMemoryForTests() {
        requestMemoryEviction()
        performPendingEviction()
    }
}
