package com.handy.android

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Centralized helper for managing marker files in [Context.getFilesDir].
 *
 * All app settings and states (active_model, model_language, llm_*, custom_words,
 * etc.) are stored as marker files in the private app files directory to enable
 * consistent, content-provider-free access across processes (e.g. main and the
 * isolated IME process).
 *
 * Port of `MarkerFileHelper.java` from android_transcribe_app (Rust/Java origin).
 * The atomic write path is the load-bearing part: concurrent writers of the same
 * marker (main process, IME service, settings UI) must never share a temp path, or
 * one writer's rename can move a file another writer is still writing to, exposing
 * partial content to readers. With per-write temps + fsync + rename, every rename
 * is atomic and readers only ever see a complete value.
 */
object MarkerFileHelper {
    private const val TAG = "MarkerFileHelper"

    /** Checks if a marker file exists in [Context.getFilesDir]. */
    fun exists(context: Context, fileName: String): Boolean {
        if (fileName.isEmpty()) return false
        return File(context.applicationContext.filesDir, fileName).exists()
    }

    /** Creates or deletes a marker file depending on [present]. */
    fun setExists(context: Context, fileName: String, present: Boolean) {
        if (fileName.isEmpty()) return
        val file = File(context.applicationContext.filesDir, fileName)
        if (present) {
            if (!file.exists()) {
                try {
                    file.createNewFile()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to create marker file: $fileName", e)
                }
            }
        } else if (file.exists()) {
            file.delete()
        }
    }

    /** Reads a UTF-8 string from a marker file. Returns [defaultValue] if absent or error. */
    fun readString(context: Context, fileName: String, defaultValue: String): String {
        if (fileName.isEmpty()) return defaultValue
        val file = File(context.applicationContext.filesDir, fileName)
        if (!file.isFile) return defaultValue
        return try {
            String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to read string marker: $fileName", e)
            defaultValue
        }
    }

    /**
     * Writes a UTF-8 string to a marker file atomically. If [value] is null or
     * empty, deletes the file. See class KDoc for the atomicity rationale.
     */
    fun writeString(context: Context, fileName: String, value: String?) {
        if (fileName.isEmpty()) return
        val dir = context.applicationContext.filesDir
        val file = File(dir, fileName)
        if (value == null || value.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        val temp = File(dir, uniqueTempName(fileName))
        try {
            FileOutputStream(temp).use { os ->
                os.write(value.toByteArray(StandardCharsets.UTF_8))
                os.fd.sync()
                if (!temp.renameTo(file)) {
                    // Fallback to direct write if rename fails across partitions.
                    Files.write(file.toPath(), value.toByteArray(StandardCharsets.UTF_8))
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write marker file: $fileName", e)
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** Reads an integer from a marker file. Returns [defaultValue] if absent or unparseable. */
    fun readInt(context: Context, fileName: String, defaultValue: Int): Int {
        val s = readString(context, fileName, null.toString())
        if (s.isEmpty()) return defaultValue
        return try {
            s.toInt()
        } catch (_: NumberFormatException) {
            defaultValue
        }
    }

    /** Writes an integer as a string to a marker file. */
    fun writeInt(context: Context, fileName: String, value: Int) {
        writeString(context, fileName, value.toString())
    }

    /** Deletes a marker file if it exists. */
    fun delete(context: Context, fileName: String) {
        if (fileName.isEmpty()) return
        val file = File(context.applicationContext.filesDir, fileName)
        if (file.exists()) file.delete()
    }

    // ----------------------------------------------------------------------
    // File-based methods for JVM unit testing without a Context
    // (mirrors the origin's *_FromFile seam so MarkerAtomicityTest runs on the JVM)
    // ----------------------------------------------------------------------

    fun readStringFromFile(dir: File, fileName: String, defaultValue: String): String {
        if (fileName.isEmpty()) return defaultValue
        val file = File(dir, fileName)
        if (!file.isFile) return defaultValue
        return try {
            String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim()
        } catch (_: IOException) {
            defaultValue
        }
    }

    fun writeStringToFile(dir: File, fileName: String, value: String?) {
        if (fileName.isEmpty()) return
        if (value == null || value.isEmpty()) {
            val file = File(dir, fileName)
            if (file.exists()) file.delete()
            return
        }
        val temp = File(dir, uniqueTempName(fileName))
        val file = File(dir, fileName)
        try {
            FileOutputStream(temp).use { os ->
                os.write(value.toByteArray(StandardCharsets.UTF_8))
                os.fd.sync()
                if (!temp.renameTo(file)) {
                    Files.write(file.toPath(), value.toByteArray(StandardCharsets.UTF_8))
                }
            }
        } catch (_: IOException) {
            // Log or fallback
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun readIntFromFile(dir: File, fileName: String, defaultValue: Int): Int {
        val s = readStringFromFile(dir, fileName, null.toString())
        if (s.isEmpty()) return defaultValue
        return try {
            s.toInt()
        } catch (_: NumberFormatException) {
            defaultValue
        }
    }

    fun writeIntToFile(dir: File, fileName: String, value: Int) {
        writeStringToFile(dir, fileName, value.toString())
    }

    /**
     * Per-write unique temp name: concurrent writers of the same marker must never
     * share a temp path (their renames would race and could expose a partially
     * written target). Thread id + a monotonic nanoTime keep names distinct across
     * threads and repeated calls.
     */
    private fun uniqueTempName(fileName: String): String =
        "$fileName.tmp${Thread.currentThread().id}-${System.nanoTime()}"
}
