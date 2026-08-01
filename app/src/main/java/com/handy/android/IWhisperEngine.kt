package com.handy.android

interface IWhisperEngine : AutoCloseable {
    fun init(modelPath: String): Boolean

    /** Initializes the model with an optional hardware backend. */
    fun initWithBackend(modelPath: String, useGpu: Boolean): Boolean = init(modelPath)

    fun transcribe(
        audioData: FloatArray,
        numThreads: Int = 4,
        translate: Boolean = false,
        language: String = "auto",
    ): String

    /** Extended transcription entry point; legacy engines safely ignore the prompt. */
    fun transcribe(
        audioData: FloatArray,
        numThreads: Int,
        translate: Boolean,
        language: String,
        initialPrompt: String,
    ): String = transcribe(audioData, numThreads, translate, language)

    /** Requests cancellation of an inference that is currently running. */
    fun cancelTranscribe() = Unit
}
