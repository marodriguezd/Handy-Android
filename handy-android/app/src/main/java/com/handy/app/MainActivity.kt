package com.handy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handy.app.di.ViewModelFactory
import com.handy.app.navigation.AppNavigation
import com.handy.app.ui.history.HistoryScreen
import com.handy.app.ui.models.ModelCatalogScreen
import com.handy.app.ui.onboarding.OnboardingScreen
import com.handy.app.ui.about.AboutContent
import com.handy.app.ui.debug.DebugScreen
import com.handy.app.ui.settings.AdvancedSettingsContent
import com.handy.app.ui.settings.GeneralSettingsContent
import com.handy.app.ui.postprocess.PostProcessScreen
import com.handy.app.ui.theme.HandyTheme
import com.handy.app.ui.theme.ThemeMode
import com.handy.app.viewmodel.HistoryViewModel
import com.handy.app.viewmodel.ModelsViewModel
import com.handy.app.viewmodel.OnboardingViewModel
import com.handy.app.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private const val SAVED_IS_DICTATING = "is_dictating"
    }

    private var savedIsDictating: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // MD3 edge-to-edge: lets `surfaceContainer*` reach behind the status
        // and navigation bars. The XML theme already declares them transparent
        // (themes.xml), this flips the WindowCompat flag.
        enableEdgeToEdge()

        val app = (application as HandyApplication)
        // Sprint 28b diagnostic — one-shot Log.i breadcrumbs to disambiguate
        // why the Debug panel nav item sometimes does not appear post-broadcast.
        // The intent is to print the StateFlow values BEFORE setContent so we
        // see exactly what the first composition will read. Wrapped in
        // `BuildConfig.DEBUG` so release builds never carry the noise.
        if (BuildConfig.DEBUG) {
            android.util.Log.i("HandyMain", "onCreate enter: debugModeFlow.value=${app.settingsStore.debugModeFlow.value}, debug(prefs)=${app.settingsStore.debugMode}")
        }
        if (intent?.getBooleanExtra("skip_onboarding", false) == true) {
            app.settingsStore.onboardingCompleted = true
            if (BuildConfig.DEBUG) android.util.Log.i("HandyMain", "skip_onboarding=true")
        }

        if (intent?.getBooleanExtra("start_dictation", false) == true) {
            app.engineViewModel.startRecording()
            if (BuildConfig.DEBUG) android.util.Log.i("HandyMain", "start_dictation=true")
        }

        if (BuildConfig.DEBUG) {
            android.util.Log.i("HandyMain", "BEFORE setContent: debugModeFlow.value=${app.settingsStore.debugModeFlow.value}, debug(prefs)=${app.settingsStore.debugMode}")
        }
        setContent {
            // Subscribe to themeMode + dynamicColor via the StateFlow exposed
            // by SettingsStore. `collectAsState()` returns a Compose `State<T>`
            // that is re-emitted whenever the underlying flow changes, so
            // user picks from the About screen propagate without an Activity
            // restart.
            // SettingsStore is a process-wide singleton, so `collectAsState()`
            // captures the live themeMode/dynamicColor value at the first
            // composition and continues to emit whenever the About screen
            // mutates `settingsStore.themeMode/dynamicColor`. No Activity
            // restart is needed after a user-driven theme switch.
            val themeModeState: State<ThemeMode> =
                app.settingsStore.themeModeFlow.collectAsState()
            val dynamicColorState: State<Boolean> =
                app.settingsStore.dynamicColorFlow.collectAsState()
            // Sprint 28b — Debug gate is now reactive. collectAsState on
            // debugModeFlow flips the AppNavigation `debugEnabled` prop
            // whenever SettingsStore.debugMode changes; AppNavigation
            // uses Option A (always-registered route + placeholder) so
            // the graph rebuild is atomic.
            val debugModeState: State<Boolean> =
                app.settingsStore.debugModeFlow.collectAsState()

            val settingsViewModel: SettingsViewModel = viewModel(
                factory = ViewModelFactory.create(app)
            )

            HandyTheme(
                themeModeState = themeModeState,
                dynamicColorState = dynamicColorState,
            ) {
                AppNavigation(
                    onboardingCompleted = app.settingsStore.onboardingCompleted,
                    onboardingContent = { onComplete ->
                        val vm: OnboardingViewModel = viewModel(
                            factory = ViewModelFactory.create(app)
                        )
                        OnboardingScreen(
                            viewModel = vm,
                            onComplete = onComplete,
                        )
                    },
                    generalTabContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            GeneralSettingsContent(viewModel = settingsViewModel)
                        }
                    },
                    advancedTabContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            AdvancedSettingsContent(viewModel = settingsViewModel)
                        }
                    },
                    modelsTabContent = {
                        val vm: ModelsViewModel = viewModel(
                            factory = ViewModelFactory.create(app)
                        )
                        ModelCatalogScreen(viewModel = vm)
                    },
                    postProcessContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            PostProcessScreen()
                        }
                    },
                    historyContent = {
                        val vm: HistoryViewModel = viewModel(
                            factory = ViewModelFactory.create(app)
                        )
                        HistoryScreen(viewModel = vm)
                    },
                    aboutContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            AboutContent()
                        }
                    },
                    // Sprint 28b — Debug destination reachability controlled
                    // by Settings.debugMode, reactively sourced via
                    // debugModeFlow.collectAsState(). Option A in
                    // AppNavigation keeps the Debug route always-registered
                    // (placeholder body when gate is false) so the graph
                    // never tears.
                    debugEnabled = debugModeState.value,
                    debugContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            DebugScreen()
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("start_dictation", false) == true) {
            (application as HandyApplication).engineViewModel.startRecording()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val app = application as HandyApplication
        savedIsDictating = app.engineViewModel.state.value == com.handy.app.viewmodel.EngineViewModel.STATE_LISTENING
        outState.putBoolean(SAVED_IS_DICTATING, savedIsDictating)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        savedIsDictating = savedInstanceState.getBoolean(SAVED_IS_DICTATING, false)
        // NOTE (Sprint 23 fix): AndroidManifest.xml declares the full flag set
        // `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode|locale|layoutDirection|density|fontScale"`
        // on MainActivity. Two consequences:
        // 1. `AppCompatDelegate.setApplicationLocales(...)` does NOT force an
        //    Activity restart — Compose recompiles strings via LocalConfiguration.
        // 2. `HandyTheme(themeModeState = ...)` propagates themeMode changes
        //    through `uiMode` instead of an Activity recreate too.
        // The engine singleton is preserved by HandyApplication regardless,
        // and the IME InputConnection reattaches cleanly because there is no
        // destroy/recreate cycle for either locale or theme switches.
        if (savedIsDictating) {
            android.util.Log.i(
                "HandyMain",
                "Recording state preserved across config change (engine singleton)",
            )
        }
    }
}
