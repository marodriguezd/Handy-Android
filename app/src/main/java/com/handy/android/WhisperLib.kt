package com.handy.android

/**
 * Small, lifecycle-safe wrapper around the native Whisper context.
 *
 * The native ABI is kept deliberately narrow so a full whisper.cpp build can
 * replace the bundled JNI implementation without changing the Android UI.
 */
class WhisperLib : IWhisperEngine {
    @Volatile
    private var context: Long = 0L

    init {
        System.loadLibrary("handy_whisper_jni")
    }

    @Synchronized
    override fun init(modelPath: String): Boolean {
        check(context == 0L) { "Whisper context is already initialized" }
        context = initContext(modelPath)
        return context != 0L
    }

    override fun transcribe(
        audioData: FloatArray,
        numThreads: Int,
        translate: Boolean,
        language: String,
    ): String {
        val nativeContext = context
        check(nativeContext != 0L) { "Whisper context is not initialized" }
        return fullTranscribe(nativeContext, audioData, numThreads, translate, language)
    }

    /** Requests cancellation without taking the lock used by native inference. */
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

    private external fun initContext(modelPath: String): Long
    private external fun freeContext(context: Long)
    private external fun fullTranscribe(
        context: Long,
        audioData: FloatArray,
        numThreads: Int,
        translate: Boolean,
        language: String,
    ): String
    private external fun cancelTranscribe(context: Long)
}
