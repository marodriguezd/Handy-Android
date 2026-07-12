package com.handy.app

import android.content.Context

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("handy_settings", Context.MODE_PRIVATE)

    var shizukuEnabled: Boolean
        get() = prefs.getBoolean("shizuku_enabled", false)
        set(value) = prefs.edit().putBoolean("shizuku_enabled", value).apply()

    var idleTimeout: Int
        get() = prefs.getInt("idle_timeout", 30)
        set(value) = prefs.edit().putInt("idle_timeout", value).apply()

    var postProcessEndpoint: String
        get() = prefs.getString("post_process_endpoint", "") ?: ""
        set(value) = prefs.edit().putString("post_process_endpoint", value).apply()

    var postProcessApiKey: String
        get() = prefs.getString("post_process_api_key", "") ?: ""
        set(value) = prefs.edit().putString("post_process_api_key", value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()
}
