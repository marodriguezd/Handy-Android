package com.handy.app.injection

import android.util.Log
import com.handy.app.SettingsStore

class InjectorRouter(
    private val shizukuInjector: ShizukuInjector,
    private val clipboardInjector: ClipboardInjector,
    private val settingsStore: SettingsStore,
) {
    private var imeInjector: ImeInjector = ImeInjector { null }

    fun setImeInjector(injector: ImeInjector) {
        imeInjector = injector
    }

    suspend fun inject(text: String): Result<Unit> {
        val strategy = selectStrategy()
        val result = strategy.inject(text)
        if (result.isSuccess) return result
        Log.w("InjectorRouter", "${strategy.displayName} failed: ${result.exceptionOrNull()}, falling back to clipboard")
        return clipboardInjector.inject(text)
    }

    private fun selectStrategy(): InjectorStrategy = when {
        imeInjector.isAvailable() -> imeInjector
        settingsStore.shizukuEnabled && shizukuInjector.isAvailable() -> shizukuInjector
        else -> clipboardInjector
    }
}
