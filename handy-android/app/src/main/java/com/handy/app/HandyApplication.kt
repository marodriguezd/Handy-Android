package com.handy.app

import android.app.Application
import com.handy.app.viewmodel.EngineViewModel

class HandyApplication : Application() {

    /** Singleton engine VM shared by MainActivity and HandyInputMethodService. */
    val engineViewModel: EngineViewModel by lazy {
        EngineViewModel(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Eager-init the engine singleton so nativeInit is called exactly once.
        engineViewModel
    }
}
