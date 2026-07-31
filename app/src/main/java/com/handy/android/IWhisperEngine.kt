package com.handy.android

interface IWhisperEngine : AutoCloseable {
    fun init(modelPath: String): Boolean
    fun transcribe(
        audioData: FloatArray,
        numThreads: Int = 4,
        translate: Boolean = false,
        language: String = "auto",
    ): String
}
