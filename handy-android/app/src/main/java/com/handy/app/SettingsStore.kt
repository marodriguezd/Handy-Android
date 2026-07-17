package com.handy.app

import android.content.Context
import com.handy.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("handy_settings", Context.MODE_PRIVATE)

    // ── Sprint 17 fix: backing MutableStateFlow fields declared BEFORE
    //    the var properties that reference them, so:
    //      • constructor init never sees an uninitialized backing field
    //        (would NPE if any future init code reads them).
    //      • setter order is "in-memory first, prefs persist last" so the
    //        StateFlow always reflects the latest decision before IO. ───────

    private val _themeModeFlow: MutableStateFlow<ThemeMode> = MutableStateFlow(
        prefs.getString("theme_mode", null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.System,
    )
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private val _dynamicColorFlow: MutableStateFlow<Boolean> = MutableStateFlow(
        prefs.getBoolean("dynamic_color", false),
    )
    val dynamicColorFlow: StateFlow<Boolean> = _dynamicColorFlow.asStateFlow()

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

    // ── Sprint 17: theme + locale persisted from MD3 selectors ────────────

    /** User-forced theme. Prefer [themeModeFlow] from Compose. */
    var themeMode: ThemeMode
        get() = _themeModeFlow.value
        set(value) {
            _themeModeFlow.value = value
            prefs.edit().putString("theme_mode", value.name).apply()
        }

    /** Opt-in to Material You dynamic color. Prefer [dynamicColorFlow]
     *  from Compose. */
    var dynamicColor: Boolean
        get() = _dynamicColorFlow.value
        set(value) {
            _dynamicColorFlow.value = value
            prefs.edit().putBoolean("dynamic_color", value).apply()
        }

    /** BCP-47 app language tag (`null` ⇒ follow `Locale.getDefault()`).
     *  Applied by `AboutContent` via `AppCompatDelegate.setApplicationLocales(...)`.
     *  Prefer [appLanguageFlow] from Compose. */
    private val _appLanguageFlow: MutableStateFlow<String?> = MutableStateFlow(
        prefs.getString("app_language", null),
    )
    val appLanguageFlow: StateFlow<String?> = _appLanguageFlow.asStateFlow()

    var appLanguage: String?
        get() = _appLanguageFlow.value
        set(value) {
            _appLanguageFlow.value = value
            val editor = prefs.edit()
            if (value == null) editor.remove("app_language") else editor.putString("app_language", value)
            editor.apply()
        }

    // ── Sprint 19: General settings (audio + model) ──────────────────────

    /** True when Handy plays a start/stop sound when dictation
     *  begins/ends. */
    var audioFeedbackEnabled: Boolean
        get() = prefs.getBoolean("audio_feedback_enabled", true)
        set(value) = prefs.edit().putBoolean("audio_feedback_enabled", value).apply()

    /** 0..1 volume applied to the start/stop sound. */
    var audioFeedbackVolume: Float
        get() = prefs.getFloat("audio_feedback_volume", 0.5f)
        set(value) = prefs.edit().putFloat("audio_feedback_volume", value).apply()

    /** Sound theme id (`handy_normal`, `handy_soft`, `handy_narrator`). */
    var soundTheme: String
        get() = prefs.getString("sound_theme", "handy_normal") ?: "handy_normal"
        set(value) = prefs.edit().putString("sound_theme", value).apply()

    /** Audio device id picked by the user; `null` means default. */
    var selectedMicrophone: String?
        get() = prefs.getString("selected_microphone", null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("selected_microphone") else editor.putString("selected_microphone", value)
            editor.apply()
        }

    /** Currently active model id (mirrors PC `selected_model`). */
    var selectedModel: String?
        get() = prefs.getString("selected_model", null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("selected_model") else editor.putString("selected_model", value)
            editor.apply()
        }

    /** Sprint 25: model-unload timeout in minutes (5, 10, 15, 30, ∞). */
    var modelUnloadTimeoutMinutes: Int
        get() = prefs.getInt("model_unload_timeout_minutes", 30)
        set(value) = prefs.edit().putInt("model_unload_timeout_minutes", value).apply()

    /** Sprint 25: custom words list used to bias the recognizer. */
    var customWords: List<String>
        get() = prefs.getString("custom_words", "")
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) {
            prefs.edit()
                .putString("custom_words", value.joinToString("\n"))
                .apply()
        }

    /** Sprint 25: append trailing space to transcriptions (PC
     *  `append_trailing_space`). */
    var appendTrailingSpace: Boolean
        get() = prefs.getBoolean("append_trailing_space", false)
        set(value) = prefs.edit().putBoolean("append_trailing_space", value).apply()

    /** Sprint 25: post-process provider id. */
    var postProcessProviderId: String
        get() = prefs.getString("post_process_provider_id", "openai") ?: "openai"
        set(value) = prefs.edit().putString("post_process_provider_id", value).apply()

    /** Sprint 26: post-process model for the active provider. */
    var postProcessModel: String
        get() = prefs.getString("post_process_model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) = prefs.edit().putString("post_process_model", value).apply()

    // ── Sprint 21: IME pill placement (top vs bottom anchor) ────────────────
    //
    // The HandyVoiceBar in `HandyInputMethodService.kt` reads this via
    // `imePlacementFlow.collectAsState()` and switches its outer Box
    // alignment / padding accordingly.  Defaults to "bottom" — the
    // historical Handy Android pill position.
    private val _imePlacementFlow: MutableStateFlow<String> = MutableStateFlow(
        prefs.getString("ime_placement", "bottom") ?: "bottom",
    )
    val imePlacementFlow: StateFlow<String> = _imePlacementFlow.asStateFlow()

    var imePlacement: String
        get() = _imePlacementFlow.value
        set(value) {
            _imePlacementFlow.value = value
            prefs.edit().putString("ime_placement", value).apply()
        }

    // ── Pre-Sprint-26 Batch C: recording repository migration flag ─────
    // Production binds the Kotlin-side [com.handy.app.audio.RecordingRepository]
    // to this flag via a thin factory in `HandyApplication` so the
    // repository itself stays JVM-testable without a Context. Sprint 25
    // is expected to surface a compose-side toggle wired through
    // [recordingDualWriteFlow].

    private val _recordingDualWriteFlow: MutableStateFlow<Boolean> = MutableStateFlow(
        prefs.getBoolean("recording_dual_write", true),
    )
    val recordingDualWriteFlow: StateFlow<Boolean> = _recordingDualWriteFlow.asStateFlow()

    var recordingDualWriteMode: Boolean
        get() = _recordingDualWriteFlow.value
        set(value) {
            _recordingDualWriteFlow.value = value
            prefs.edit().putBoolean("recording_dual_write", value).apply()
        }
}
