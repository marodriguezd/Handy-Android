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

    var batteryOptimizationExempt: Boolean
        get() = prefs.getBoolean("battery_optimization_exempt", false)
        set(value) = prefs.edit().putBoolean("battery_optimization_exempt", value).apply()

    var experimentalEnabled: Boolean
        get() = prefs.getBoolean("experimental_enabled", false)
        set(value) = prefs.edit().putBoolean("experimental_enabled", value).apply()

    var vadEnabled: Boolean
        get() = prefs.getBoolean("vad_enabled", true)
        set(value) = prefs.edit().putBoolean("vad_enabled", value).apply()

    var addFinalSpace: Boolean
        get() = prefs.getBoolean("add_final_space", false)
        set(value) = prefs.edit().putBoolean("add_final_space", value).apply()

    var postProcessingEnabled: Boolean
        get() = prefs.getBoolean("post_processing_enabled", true)
        set(value) = prefs.edit().putBoolean("post_processing_enabled", value).apply()

    var autoSend: String
        get() = prefs.getString("auto_send", "disabled") ?: "disabled"
        set(value) = prefs.edit().putString("auto_send", value).apply()

    /** Show experimental (Moonshine Base monolingual) models in the catalog. */
    var showExperimentalModels: Boolean
        get() = prefs.getBoolean("show_experimental_models", false)
        set(value) = prefs.edit().putBoolean("show_experimental_models", value).apply()
}
