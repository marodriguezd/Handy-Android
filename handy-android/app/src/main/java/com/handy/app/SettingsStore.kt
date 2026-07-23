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

    var customWords: Set<String>
        get() = prefs.getStringSet("custom_words", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("custom_words", value).apply()

    var fillerWordsEnabled: Boolean
        get() = prefs.getBoolean("filler_words_enabled", true)
        set(value) = prefs.edit().putBoolean("filler_words_enabled", value).apply()

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

    /** Transcription ASR language tag (`"auto"`, `"es"`, `"en"`, etc., matching PC `selected_language`). */
    private val _selectedLanguageFlow: MutableStateFlow<String> = MutableStateFlow(
        prefs.getString("selected_language", "auto") ?: "auto",
    )
    val selectedLanguageFlow: StateFlow<String> = _selectedLanguageFlow.asStateFlow()

    var selectedLanguage: String
        get() = _selectedLanguageFlow.value
        set(value) {
            _selectedLanguageFlow.value = value
            prefs.edit().putString("selected_language", value).apply()
        }

    /** Sprint 25: model-unload timeout in minutes (5, 10, 15, 30, ∞). */
    var modelUnloadTimeoutMinutes: Int
        get() = prefs.getInt("model_unload_timeout_minutes", 30)
        set(value) = prefs.edit().putInt("model_unload_timeout_minutes", value).apply()

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

    /** Sprint 26: user-defined prompt templates. Persisted as a
     *  newline-separated list of strings; the post-process screen
     *  wraps each in a [com.handy.app.ui.postprocess.PostProcessPrompt]
     *  for editing (name + text). Default empty list. */
    var postProcessPrompts: List<String>
        get() = prefs.getString("post_process_prompts", "")
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) {
            prefs.edit()
                .putString("post_process_prompts", value.joinToString("\n"))
                .apply()
        }

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

    // ── Sprint 25b Phase C — Advanced Settings controls ─────────────
    //
    // Persistence-half only. The actual `AccelerationBackend` selection
    // does NOT yet route into Rust — the JVM-attached engine still picks
    // CPU by default. The choice is rendered + stored as a UI hint
    // for the future Sprint 26+ backend wiring.

    private val _customWordsRawFlow: MutableStateFlow<String> = MutableStateFlow(
        prefs.getString("custom_words_raw", "") ?: "",
    )
    val customWordsRawFlow: StateFlow<String> = _customWordsRawFlow.asStateFlow()

    var customWordsRaw: String
        get() = _customWordsRawFlow.value
        set(value) {
            _customWordsRawFlow.value = value
            prefs.edit().putString("custom_words_raw", value).apply()
        }

    private val _historyLimitFlow: MutableStateFlow<com.handy.app.settings.HistoryLimit> = MutableStateFlow(
        runCatching {
            com.handy.app.settings.HistoryLimit.valueOf(
                prefs.getString("history_limit", "Unlimited") ?: "Unlimited"
            )
        }.getOrDefault(com.handy.app.settings.HistoryLimit.Unlimited),
    )
    val historyLimitFlow: StateFlow<com.handy.app.settings.HistoryLimit> = _historyLimitFlow.asStateFlow()

    var historyLimit: com.handy.app.settings.HistoryLimit
        get() = _historyLimitFlow.value
        set(value) {
            _historyLimitFlow.value = value
            prefs.edit().putString("history_limit", value.name).apply()
        }

    private val _retentionPeriodFlow: MutableStateFlow<com.handy.app.settings.RetentionPeriod> = MutableStateFlow(
        runCatching {
            com.handy.app.settings.RetentionPeriod.valueOf(
                prefs.getString("retention_period", "Never") ?: "Never"
            )
        }.getOrDefault(com.handy.app.settings.RetentionPeriod.Never),
    )
    val retentionPeriodFlow: StateFlow<com.handy.app.settings.RetentionPeriod> = _retentionPeriodFlow.asStateFlow()

    var retentionPeriod: com.handy.app.settings.RetentionPeriod
        get() = _retentionPeriodFlow.value
        set(value) {
            _retentionPeriodFlow.value = value
            prefs.edit().putString("retention_period", value.name).apply()
        }

    private val _accelerationBackendFlow: MutableStateFlow<com.handy.app.settings.AccelerationBackend> = MutableStateFlow(
        runCatching {
            com.handy.app.settings.AccelerationBackend.valueOf(
                prefs.getString("acceleration_backend", "CPU") ?: "CPU"
            )
        }.getOrDefault(com.handy.app.settings.AccelerationBackend.CPU),
    )
    val accelerationBackendFlow: StateFlow<com.handy.app.settings.AccelerationBackend> = _accelerationBackendFlow.asStateFlow()

    var accelerationBackend: com.handy.app.settings.AccelerationBackend
        get() = _accelerationBackendFlow.value
        set(value) {
            _accelerationBackendFlow.value = value
            prefs.edit().putString("acceleration_backend", value.name).apply()
        }

    // ── Sprint 28 — Debug panel gating ────────────────────────────
    //
    // Defaults to `false` so the Debug destination is hidden from
    // the regular Settings tree (and absent from the nav rail/bar)
    // until a developer explicitly flips the flag. AppNavigation.kt
    // reads this StateFlow and conditionally adds `Screen.Debug` to
    // the nav list. The flag is wired to the future `Settings` screen
    // toggle in Sprint 28b; for Sprint 28 MVP the value is settable
    // via direct write through `app.settingsStore.debugMode = true`.
    //
    // The `debug_mode` SharedPreferences key is the canonical storage;
    // the in-memory backing MutableStateFlow mirrors it so Compose
    // observers re-compose reactively (the same pattern as
    // `recordingDualWriteMode` from Sprint 25b).
    private val _debugModeFlow: MutableStateFlow<Boolean> = MutableStateFlow(
        prefs.getBoolean("debug_mode", false),
    )
    val debugModeFlow: StateFlow<Boolean> = _debugModeFlow.asStateFlow()

    var debugMode: Boolean
        get() = _debugModeFlow.value
        set(value) {
            _debugModeFlow.value = value
            prefs.edit().putBoolean("debug_mode", value).apply()
        }

    // ── Sprint 28b — Debug panel settings (5 new fields) ──────────────────
    //
    // Each backs a Compose component in `ui/debug/components/` and is
    // persisted via SharedPreferences with a sibling MutableStateFlow
    // so observers re-compose reactively. Defaults match the post-Sprint
    // baseline so installing fresh leaves a sensible developer-facing
    // configuration that can be tweaked from the Debug panel itself.

    /** Log verbosity for the Debug panel's LiveLogViewer. One of
     *  "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR". The Compose
     *  LogLevelSelector uses the same strings as a key into the
     *  HandyDropdown options. Default "INFO" mirrors the production
     *  android.util.Log call. */
    private val _logLevelFlow: MutableStateFlow<String> = MutableStateFlow(
        prefs.getString("log_level", "INFO") ?: "INFO",
    )
    val logLevelFlow: StateFlow<String> = _logLevelFlow.asStateFlow()

    var logLevel: String
        get() = _logLevelFlow.value
        set(value) {
            _logLevelFlow.value = value
            prefs.edit().putString("log_level", value).apply()
        }

    /** True when the app checks for updates on every launch. Toggled
     *  from the Debug panel's UpdateChecksToggle. Default `true` so
     *  releases stay current; the Sprint 28b DebugPanel allows
     *  developers to silence this for offline testing. */
    private val _updateChecksOnLaunchFlow: MutableStateFlow<Boolean> = MutableStateFlow(
        prefs.getBoolean("update_checks_on_launch", true),
    )
    val updateChecksOnLaunchFlow: StateFlow<Boolean> = _updateChecksOnLaunchFlow.asStateFlow()

    var updateChecksOnLaunch: Boolean
        get() = _updateChecksOnLaunchFlow.value
        set(value) {
            _updateChecksOnLaunchFlow.value = value
            prefs.edit().putBoolean("update_checks_on_launch", value).apply()
        }

    /** Delay after `Clipboard.setPrimaryClip` before injecting the
     *  paste key event (ms). Previously hardcoded `delay(50L)` in
     *  ShizukuInjector.kt; sensitised to user input so flaky targets
     *  can stretch it (e.g. slow remote-app composition). Default
     *  50 ms to match the historical behavior. Range 0..1000. */
    private val _pasteDelayMsFlow: MutableStateFlow<Int> = MutableStateFlow(
        prefs.getInt("paste_delay_ms", 50),
    )
    val pasteDelayMsFlow: StateFlow<Int> = _pasteDelayMsFlow.asStateFlow()

    var pasteDelayMs: Int
        get() = _pasteDelayMsFlow.value
        set(value) {
            _pasteDelayMsFlow.value = value.coerceIn(0, 1000)
            prefs.edit().putInt("paste_delay_ms", _pasteDelayMsFlow.value).apply()
        }

    /** Frames reserved in the AAudio pipeline ring buffer. Larger
     *  values handle longer utterances without dropouts at the cost
     *  of fixed memory. The Rust pipeline currently reserves 262_144
     *  frames at 16 kHz (~16 s). Default matches production. */
    private val _recordingBufferFramesFlow: MutableStateFlow<Int> = MutableStateFlow(
        prefs.getInt("recording_buffer_frames", 262_144),
    )
    val recordingBufferFramesFlow: StateFlow<Int> = _recordingBufferFramesFlow.asStateFlow()

    var recordingBufferFrames: Int
        get() = _recordingBufferFramesFlow.value
        set(value) {
            // Snap to the nearest 4096-multiple to keep page-aligned
            // memory contracts on most allocators and to make the
            // slider UI feel key-stopped.
            val aligned = ((value.coerceIn(8192, 1_048_576) + 2047) / 4096) * 4096
            _recordingBufferFramesFlow.value = aligned
            prefs.edit().putInt("recording_buffer_frames", aligned).apply()
        }

    /** True when the foreground recording service keeps the
     *  microphone on between dictations (no pre-roll). Toggled from
     *  the Debug panel; default `false` to match the production
     *  preference that hands the mic back to other apps quickly. */
    private val _alwaysOnMicrophoneFlow: MutableStateFlow<Boolean> = MutableStateFlow(
        prefs.getBoolean("always_on_microphone", false),
    )
    val alwaysOnMicrophoneFlow: StateFlow<Boolean> = _alwaysOnMicrophoneFlow.asStateFlow()

    var alwaysOnMicrophone: Boolean
        get() = _alwaysOnMicrophoneFlow.value
        set(value) {
            _alwaysOnMicrophoneFlow.value = value
            prefs.edit().putBoolean("always_on_microphone", value).apply()
        }
}
