package com.handy.app.injection

import android.util.Log
import com.handy.app.BuildConfig
import com.handy.app.SettingsStore

class InjectorRouter(
    private val shizukuInjector: InjectorStrategy,
    private val clipboardInjector: InjectorStrategy,
    private val settingsStore: SettingsStore,
) {
    private var imeInjector: InjectorStrategy = ImeInjector { null }

    fun setImeInjector(injector: InjectorStrategy) {
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
        !BuildConfig.DEBUG && settingsStore.shizukuEnabled && shizukuInjector.isAvailable() -> shizukuInjector
        else -> clipboardInjector
    }
}
