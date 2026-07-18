package com.handy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.handy.app.di.ViewModelFactory
import com.handy.app.navigation.AppNavigation
import com.handy.app.navigation.FoldingFeatureInfo
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
            // Sprint 29b — predictive back gesture (Android 14+) opt-in
            // architecture note:
            //
            // The user brief asked for "PredictiveBackHandler integration
            // en MainActivity.kt". `PredictiveBackHandler` is a @Composable
            // function from `androidx.activity.compose` — it can only live
            // inside a Compose composition, not in MainActivity.onCreate's
            // non-composable context. The architectural location for it
            // is therefore [com.handy.app.navigation.AppNavigation], where
            // the NavController instance lives in scope.
            //
            // MainActivity's contribution to the predictive-back wiring
            // is:
            //   (a) `enableEdgeToEdge()` — Sprint 17. Required for
            //       edge-to-edge so the system-rendered back-gesture
            //       animation has full window extent.
            //   (b) Calling `AppNavigation(...)` below — AppNavigation
            //       owns the root-level `PredictiveBackHandler` block
            //       that covers all 6 destinations in the NavHost.
            //
            // AndroidManifest.xml's `enableOnBackInvokedCallback="true"`
            // at `<application>` is the manifest half of the same wiring;
            // without it, the Compose `PredictiveBackHandler` does not
            // engage the predictive-back framework on Android 14+.

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

            // Sprint 29c — foldable hinge observation. WindowInfoTracker
            // exposes a Flow<WindowLayoutInfo> keyed off the Activity; the
            // `produceState` coroutine auto-cancels when the composition
            // leaves (Activity destroy / process death). The downstream
            // FoldingFeatureInfo is a parallel data class so AppNavigation.kt
            // does NOT need androidx.window on its classpath — see
            // [FoldingFeatureInfo] in com.handy.app.navigation.FoldPresentation.
            // The mapping (isSeparating + state + orientation + bounds) is
            // pruned to only what the fold-aware padding computation needs;
            // recompose cost is bounded by one WindowLayoutInfo emission per
            // posture change.
            val foldInfoState: State<FoldingFeatureInfo?> = produceState<FoldingFeatureInfo?>(
                initialValue = null,
                key1 = this@MainActivity,
            ) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { info ->
                        val feature = info.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()
                        value = feature?.let {
                            FoldingFeatureInfo(
                                isSeparating = it.isSeparating,
                                isHalfOpened = it.state == FoldingFeature.State.HALF_OPENED,
                                isHorizontal = it.orientation == FoldingFeature.Orientation.HORIZONTAL,
                                boundsTop = it.bounds.top,
                                boundsBottom = it.bounds.bottom,
                            )
                        }
                    }
            }

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
                    // Sprint 30c-#1: drop the redundant Column+verticalScroll
                    // wrapper. Column.verticalScroll cannot accept
                    // Constraints.Infinity maxHeight; when the parent
                    // AnimatedContent emits Infinity during transitions /
                    // intrinsic queries, the runtime check trips:
                    //   "Vertically scrollable component was measured with
                    //    an infinity maximum height constraints"
                    // and (per thinker diagnosis) it ALSO propagates a
                    // negative-width constraint to ListItem downstream,
                    // producing the `maxWidth(-7) must be >= minWidth(0)`
                    // IllegalArgumentException in `ListItemMeasurePolicy`.
                    //
                    // The canonical fix (mirrors Sprint 28c-#1 PostProcess
                    // and Sprint 28c-#2 AboutContent): make the destination
                    // COMPOSABLE OWN ITS OWN SCROLL SURFACE. SettingScreen.kt's
                    // `GeneralSettingsContent` + `AdvancedSettingsContent` now
                    // host LazyColumn internally — see their migrated bodies.
                    generalTabContent = {
                        GeneralSettingsContent(viewModel = settingsViewModel)
                    },
                    advancedTabContent = {
                        AdvancedSettingsContent(viewModel = settingsViewModel)
                    },
                    modelsTabContent = {
                        val vm: ModelsViewModel = viewModel(
                            factory = ViewModelFactory.create(app)
                        )
                        ModelCatalogScreen(viewModel = vm)
                    },
                    postProcessContent = {
                        // Sprint 28c-closed: PostProcessScreen body migrated
                        // from Column.verticalScroll to LazyColumn, which
                        // handles Infinity bounds correctly (Column.verticalScroll
                        // crashed on A059 with the runtime
                        // "infinity maximum height constraints" check). The
                        // redundant outer Column.verticalScroll wrapper is
                        // REMOVED — the inner LazyColumn now owns the scroll
                        // surface, matching the HistoryScreen /
                        // ModelCatalogScreen pattern. See AGENTS.md Sprint 28c
                        // closure.
                        PostProcessScreen()
                    },
                    historyContent = {
                        val vm: HistoryViewModel = viewModel(
                            factory = ViewModelFactory.create(app)
                        )
                        HistoryScreen(viewModel = vm)
                    },
        aboutContent = {
            // Sprint 28c-#2: AboutContent.kt migrated to LazyColumn
            // (Column.fillMaxWidth() → LazyColumn.fillMaxSize()) for
            // parity with HistoryScreen/ModelCatalogScreen/PostProcessScreen.
            // AnimatedContent-supplied Infinity is now safely consumed
            // (LazyColumn measures only visible items, not intrinsic).
            AboutContent()
        },
                    // Sprint 28b — Debug destination reachability controlled
                    // by Settings.debugMode, reactively sourced via
                    // debugModeFlow.collectAsState(). Option A in
                    // AppNavigation keeps the Debug route always-registered
                    // (placeholder body when gate is false) so the graph
                    // never tears.
                    debugEnabled = debugModeState.value,
                    // Sprint 28b-v15 — removed outer Column.verticalScroll
                    // wrapper; DebugScreen already owns its own LazyColumn.
                    // The wrapper was the runtime-check target for
                    //   "Vertically scrollable component measured with
                    //    infinity maximum height constraints"
                    // on A059 Android 16. See AGENTS.md Sprint 28b-v15
                    // closure for the full diagnosis chain.
                    debugContent = { DebugScreen() },
                    // Sprint 29c — foldable-aware padding is driven here.
                    // AppNavigation converts the fold state to dp-typed
                    // top + bottom padding and applies it to TopAppBar and
                    // the BottomBar (HandyBottomNavigation). Mirrors the
                    // Sprint 28b-v15 + Sprint 29b architecture pattern: the
                    // Activity owns the platform integration (WindowInfoTracker
                    // is Activity-scoped), the Composable layer receives the
                    // already-mapped state and renders.
                    foldInfo = foldInfoState.value,
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
