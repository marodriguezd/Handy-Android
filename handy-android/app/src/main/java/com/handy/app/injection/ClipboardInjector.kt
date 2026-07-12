package com.handy.app.injection

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class ClipboardInjector(
    private val context: Context,
) : InjectorStrategy {

    override val displayName: String get() = "Clipboard"

    override fun isAvailable(): Boolean = true

    override suspend fun inject(text: String): Result<Unit> {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Handy Dictation", text))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
