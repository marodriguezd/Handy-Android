package com.handy.app.injection

import android.view.inputmethod.InputConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImeInjector(
    private val inputConnectionProvider: () -> InputConnection?,
) : InjectorStrategy {

    override val displayName: String get() = "IME InputConnection"

    @Volatile
    private var cachedConnection: InputConnection? = null

    override fun isAvailable(): Boolean {
        val ic = inputConnectionProvider()
        cachedConnection = ic
        return ic != null
    }

    override suspend fun inject(text: String): Result<Unit> = withContext(Dispatchers.Main) {
        val ic = cachedConnection ?: inputConnectionProvider()
            ?: return@withContext Result.failure(IllegalStateException("IME InputConnection not available"))
        cachedConnection = null
        return@withContext try {
            ic.commitText(text, 1)
            ic.finishComposingText()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
