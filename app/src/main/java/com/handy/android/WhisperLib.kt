package com.handy.android

/** Lifecycle-safe wrapper around the native Whisper context. */
class WhisperLib : IWhisperEngine {
    @Volatile
    private var context: Long = 0L

    init {
        System.loadLibrary("handy_whisper_jni")
    }

    @Synchronized
    override fun init(modelPath: String): Boolean = initWithBackend(modelPath, useGpu = false)

    @Synchronized
    override fun initWithBackend(modelPath: String, useGpu: Boolean): Boolean {
        check(context == 0L) { "Whisper context is already initialized" }
        context = initContext(modelPath, useGpu)
        return context != 0L
    }

    override fun transcribe(
        audioData: FloatArray,
        numThreads: Int,
        translate: Boolean,
        language: String,
    ): String = transcribe(audioData, numThreads, translate, language, "")

    override fun transcribe(
        audioData: FloatArray,
        numThreads: Int,
        translate: Boolean,
        language: String,
        initialPrompt: String,
    ): String {
        val nativeContext = context
        check(nativeContext != 0L) { "Whisper context is not initialized" }
        return fullTranscribe(nativeContext, audioData, numThreads, translate, language, initialPrompt)
    }

    override fun cancelTranscribe() {
        val nativeContext = context
        if (nativeContext != 0L) cancelTranscribe(nativeContext)
    }

    @Synchronized
    override fun close() {
        if (context != 0L) {
            freeContext(context)
            context = 0L
        }
    }

    private external fun initContext(modelPath: String, useGpu: Boolean): Long
    private external fun freeContext(context: Long)
    private external fun fullTranscribe(
        context: Long,
        audioData: FloatArray,
        numThreads: Int,
        translate: Boolean,
        language: String,
        initialPrompt: String,
    ): String
    private external fun cancelTranscribe(context: Long)
}
