package com.handy.app

import android.app.Application
import android.util.Log
import com.handy.app.injection.ClipboardInjector
import com.handy.app.injection.InjectorRouter
import com.handy.app.injection.ShizukuInjector
import com.handy.app.viewmodel.EngineViewModel
import moe.shizuku.api.Shizuku

class HandyApplication : Application() {

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val shizukuInjector: ShizukuInjector by lazy { ShizukuInjector(this) }

    val clipboardInjector: ClipboardInjector by lazy { ClipboardInjector(this) }

    val injectorRouter: InjectorRouter by lazy {
        InjectorRouter(
            shizukuInjector = shizukuInjector,
            clipboardInjector = clipboardInjector,
            settingsStore = settingsStore,
        )
    }

    /** Singleton engine VM shared by MainActivity and HandyInputMethodService. */
    val engineViewModel: EngineViewModel by lazy {
        EngineViewModel(this, injectorRouter)
    }

    override fun onCreate() {
        super.onCreate()
        Shizuku.initialize(this)
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (grantResult == Shizuku.PERMISSION_GRANTED) {
                Log.i("HandyApp", "Shizuku permission granted (code=$requestCode)")
                shizukuInjector.bindService()
            }
        }
        shizukuInjector.bindService()
        engineViewModel
    }
}
