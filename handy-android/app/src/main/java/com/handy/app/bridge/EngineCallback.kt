package com.handy.app.bridge

/**
 * Callbacks invoked by the Rust engine via JNI.
 *
 * IMPLEMENTATION NOTE: All callbacks are invoked from a Rust-managed thread
 * that has been attached to the JVM via JavaVM::attach_current_thread().
 * Implementations must post to the main thread if updating UI.
 */
interface EngineCallback {

    /**
     * Engine state transition.
     * @param state  0=Idle, 1=Loading, 2=Listening, 3=Transcribing, 4=Error
     */
    fun onStateChange(state: Int)

    /**
     * Transcription result.
     * @param text       The transcribed text
     * @param isPartial  true = intermediate streaming result, false = final committed text
     */
    fun onTranscription(text: String, isPartial: Boolean)

    /**
     * Voice activity level for the audio level meter.
     * @param level  Probability [0.0, 1.0], updated ~10 times/sec
     */
    fun onVadLevel(level: Float)

    /**
     * Error callback.
     * @param code    Machine-readable error code
     * @param message Human-readable error description
     */
    fun onError(code: Int, message: String)

    /**
     * Model download progress.
     * @param modelId      The model being downloaded
     * @param bytesSoFar   Bytes downloaded
     * @param totalBytes   Total expected bytes (-1 if unknown)
     */
    fun onDownloadProgress(modelId: String, bytesSoFar: Long, totalBytes: Long)

    /**
     * Model download completed (or failed).
     * @param modelId  The model ID
     * @param success  true if download and verification succeeded
     * @param errorMsg null on success, error description on failure
     */
    fun onDownloadComplete(modelId: String, success: Boolean, errorMsg: String?)
}
