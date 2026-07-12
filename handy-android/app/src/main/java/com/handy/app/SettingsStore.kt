package com.handy.app

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("handy_settings", Context.MODE_PRIVATE)

    var shizukuEnabled: Boolean
        get() = prefs.getBoolean("shizuku_enabled", false)
        set(value) = prefs.edit().putBoolean("shizuku_enabled", value).apply()
}
