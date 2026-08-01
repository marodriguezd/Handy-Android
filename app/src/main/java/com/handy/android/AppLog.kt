package com.handy.android

import android.content.Context
import android.util.Log
import java.io.File

/** Small app-private log sink used by the diagnostics screen. */
object AppLog {
    private const val FILE_NAME = "handy.log"
    private const val MAX_BYTES = 512 * 1024
    private val lock = Any()

    fun record(context: Context, level: String, tag: String, message: String, error: Throwable? = null) {
        val line = buildString {
            append(System.currentTimeMillis())
            append(" ")
            append(level)
            append("/")
            append(tag)
            append(": ")
            append(message)
            error?.let { append(" — ").append(it.stackTraceToString()) }
            append("\n")
        }
        when (level) {
            "E" -> Log.e(tag, message, error)
            "W" -> Log.w(tag, message, error)
            "I" -> Log.i(tag, message, error)
            else -> Log.d(tag, message, error)
        }
        synchronized(lock) {
            runCatching {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                file.appendText(line)
                if (file.length() > MAX_BYTES) {
                    val retained = file.readText().takeLast(MAX_BYTES)
                    file.writeText(retained)
                }
            }
        }
    }

    fun read(context: Context): String = synchronized(lock) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).takeIf(File::isFile)?.readText().orEmpty() }
            .getOrDefault("")
    }

    fun clear(context: Context) = synchronized(lock) {
        File(context.applicationContext.filesDir, FILE_NAME).delete()
    }
}
