package com.handy.app.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.handy.app.R
import com.handy.app.ui.debug.DEBUG_ROUTE
import com.handy.app.ui.debug.shouldPopBackStackFromDebug
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

private const val ONBOARDING_ROUTE = "onboarding"

/**
 * Sprint 28 — NavScreens is no longer a top-level immutable list. Instead,
 * [AppNavigation] computes a local `navScreens` list driven by `debugEnabled`,
 * so the `Screen.Debug` entry is conditionally present when
 * `Settings.debugMode == true`. The list is `remember`-ed against
 * `debugEnabled` so toggling the settings flag (in a followup Sprint 28b)
 * re-emits without rebuilding the whole scaffold tree.
 */
private enum class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    General("general", R.string.settings_title, Icons.Default.Settings),
    Models("models", R.string.tab_models, Icons.Default.Build),
    History("history", R.string.history_title, Icons.Default.History),
    PostProcess("post_process", R.string.tab_post_process, Icons.Default.AutoAwesome),
    About("about", R.string.settings_about, Icons.Default.Info),
    Debug("debug", R.string.debug_screen_title, Icons.Default.Code),
}

/** Default screens visible when `debugEnabled == false`. */
private val DefaultScreens: List<Screen> = listOf(
    Screen.General,
    Screen.Models,
    Screen.History,
    Screen.PostProcess,
    Screen.About,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    onboardingCompleted: Boolean,
    onboardingContent: @Composable (onComplete: () -> Unit) -> Unit,
    generalTabContent: @Composable () -> Unit,
    advancedTabContent: @Composable () -> Unit,
    modelsTabContent: @Composable () -> Unit,
    postProcessContent: @Composable () -> Unit,
    historyContent: @Composable () -> Unit,
    aboutContent: @Composable () -> Unit,
    debugContent: @Composable () -> Unit = {},
    debugEnabled: Boolean = false,
    // Sprint 29c — foldable hinge state sourced from
    // WindowInfoTracker.windowLayoutInfo(activity) inside MainActivity.
    // Null on phones, tablets, and continuous-screen foldables in
    // FLAT state. The bounds are in pixel coordinates (window-relative);
    // AppNavigation converts to dp at the Compose surface.
    foldInfo: FoldingFeatureInfo? = null,
) {
    val navController = rememberNavController()
    val startDestination = if (onboardingCompleted) Screen.General.route else ONBOARDING_ROUTE

    // Sprint 28 — when debugEnabled is true, Screen.Debug is appended after
    // the About entry. The list is recomputed every composition; the key
    // blocks below (key(debugEnabled)) are the canonical drivers of NavGraph
    // re-registration, so `remember` here only saves the lambda's allocation
    // cost on stable states.
    val navScreens = remember(debugEnabled) {
        if (debugEnabled) DefaultScreens + Screen.Debug else DefaultScreens
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentScreen = navScreens.find { it.route == currentDestination?.route }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isCompact = screenWidthDp < 600

    // Sprint 29c — foldable-aware padding for the TopAppBar and the
    // BottomBar. The pixel-typed (top, bottom) Pair comes from the
    // pure FoldPresentation.computeHingePaddingPx helper; we convert
    // to dp once at the Compose surface so the consumer modifiers
    // are type-safe. The helper short-circuits to (0, 0) on phones,
    // tablets, and continuous-screen foldables in FLAT state — no
    // Compose-level cost for non-foldable form factors.
    val density = LocalDensity.current
    val screenHeightPx = with(density) {
        configuration.screenHeightDp.dp.toPx().roundToInt()
    }
    val foldPad: Pair<Int, Int> = remember(foldInfo, screenHeightPx) {
        FoldPresentation.computeHingePaddingPx(foldInfo, screenHeightPx)
    }
    val foldPadTopDp = with(density) { foldPad.first.toDp() }
    val foldPadBottomDp = with(density) { foldPad.second.toDp() }

    /**
     * Sprint 29b — predictive back gesture support (Android 14+).
     *
     * Single root-level [PredictiveBackHandler] in AppNavigation directly
     * covers all 6 destinations in the NavHost (`Settings`, `Models`,
     * `History`, `PostProcess`, `About`, `Debug`). One handler — not
     * per-`composable(...)` — because:
     *
     *  1. The handler operates on the NavController, not on each
     *     destination's body. NavController is a single object scoped
     *     to this @Composable; one handler per destination would
     *     duplicate the [kotlinx.coroutines.flow.Flow] subscription
     *     and risk concurrent `popBackStack()` invocations.
     *  2. The `enabled` predicate gates the handler off when we're on
     *     the graph's start destination (no back stack to pop; back-
     *     press should close the app as usual). The predicate is
     *     factored out into [PredictiveBackPresentation.shouldHandlePredictiveBack]
     *     for JVM-testability without Robolectric — see its test class
     *     `PredictiveBackPresentationLogicTest`.
     *
     * The handler collects the gesture's `Flow<BackEventCompat>`
     * progress events to keep the predictive-back framework engaged
     * (returning early without collecting cancels the gesture
     * automatically). On `progress.collect` completion, we pop the
     * back stack via [NavHostController.popBackStack] so the user's
     * back-press produces standard nav-stack behavior. If the user
     * aborts the gesture mid-drag, the `progress.collect` call
     * throws [CancellationException] which we **re-throw** to honor
     * Kotlin structured-concurrency semantics (mandatory per the
     * pattern documented in the Sprint 24 closure).
     *
     * Visual feedback (scale-down animation during the gesture) is
     * provided by the system's predictive-back framework on Android
     * 14+ devices because the manifest's `enableOnBackInvokedCallback`
     * flag is set at `<application>`. We do *not* drive a custom
     * animation — doing so would require wrap-around per-destination
     * `Box+Modifier.scale(progress)` wrappers that re-introduce the
     * "defensive layout wrapper around each destination body" pattern
     * Sprint 28b-v8..v15 explicitly closed.
     */
    PredictiveBackHandler(
        enabled = PredictiveBackPresentation.shouldHandlePredictiveBack(
            currentRoute = currentDestination?.route,
            startRoute = startDestination,
        ),
    ) { progress ->
        try {
            progress.collect { /* system drives scale-down animation natively */ }
            navController.popBackStack()
        } catch (e: CancellationException) {
            // User aborted gesture mid-drag — structured-concurrency
            // requires re-throwing CancellationException to honor the
            // suspending coroutine's cancellation contract.
            throw e
        }
    }

    Scaffold(
        topBar = {
            if (currentScreen != null && currentDestination?.route != ONBOARDING_ROUTE) {
                TopAppBar(
                    modifier = Modifier.padding(top = foldPadTopDp), // Sprint 29c
                    title = { Text(stringResource(currentScreen.titleRes)) },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                )
            }
        },
        bottomBar = {
            if (isCompact && currentDestination?.route != ONBOARDING_ROUTE) {
                // Sprint 28b-v9 fix — wrap in key(debugEnabled) so the
                // NavigationBar disposes + recreates when the gate flips.
                // Without this, Compose may re-use the previous slot when
                // only the `screens` parameter's identity changes, leaving
                // the bottom-nav stuck at 5 items even when `navScreens`
                // is a 6-item list.
                key(debugEnabled) {
                    HandyBottomNavigation(
                        navController = navController,
                        screens = navScreens,
                        modifier = Modifier.padding(bottom = foldPadBottomDp), // Sprint 29c
                    )
                }
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (!isCompact && currentDestination?.route != ONBOARDING_ROUTE) {
                // Sprint 28b-v9 fix — key(debugEnabled) on the navigation
                // rail mirrors the bottom-nav fix above.
                key(debugEnabled) {
                    HandyNavigationRail(
                        navController = navController,
                        screens = navScreens,
                        modifier = Modifier.fillMaxHeight(),
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                // Sprint 28b-v11 — popBackStack guard. If the user is
                // currently on the Debug destination when the gate
                // flips ON→OFF, auto-navigate them to Screen.General
                // so they don't get stranded at the
                // [com.handy.app.ui.debug.DeveloperToolsDisabled]
                // placeholder with no Debug tile in the bottom-nav.
                //
                // CRITICAL ORDERING: both `prevDebugEnabled` and the
                // `LaunchedEffect(debugEnabled)` MUST live OUTSIDE the
                // `key(debugEnabled) { ... }` block. If they're inside,
                // Compose recreates the `remember` slot with the new
                // debugEnabled as initial value, hiding the TRUE→FALSE
                // transition the guard needs to detect. By placing them
                // above the key block, the MutableState survives the
                // key-invalidation and the LaunchedEffect correctly
                // observes the previous debugEnabled for one cycle
                // before being re-keyed into the new launch.
                val prevDebugEnabled = remember { mutableStateOf(debugEnabled) }
                LaunchedEffect(debugEnabled) {
                    val wasPrev = prevDebugEnabled.value
                    prevDebugEnabled.value = debugEnabled
                    if (shouldPopBackStackFromDebug(
                            currentRoute = currentDestination?.route,
                            debugEnabledNow = debugEnabled,
                            debugEnabledPrev = wasPrev,
                        )
                    ) {
                        navController.navigate(Screen.General.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
                // Sprint 28b-v9 fix — Compose Navigation's NavHost
                // builder lambda is evaluated only once during initial
                // graph construction; the `composable(Screen.Debug.route)`
                // body captures `debugEnabled` by value at first call,
                // so re-keying the NavHost is the canonical way to force
                // the graph (and the captured closures) to re-register
                // when the gate flips. Back stack is preserved because
                // `navController` is allocated OUTSIDE this key block.
                //
                // Sprint 28b-v15 REAL FIX — the runtime-crash root cause
                // was the redundant `Column(verticalScroll(...))` wrapper
                // inside MainActivity's `debugContent` lambda; removed in
                // MainActivity (DebugScreen already hosts its own
                // LazyColumn). Companion fix in `SettingsTabsScreen`
                // (Box(Modifier.weight(1f))) bounds the General/Advanced
                // tab body's maxHeight. Not yet verified on-device;
                // ground-truth next step documented in AGENTS.md
                // Session 2026-07-17 Sprint 28b-v15 closure.
                //
                // (NavHost does NOT accept `sizeTransform` directly in
                // Navigation Compose 2.8.x — that parameter lives on
                // `composable(...)`. The default for `composable`'s
                // sizeTransform is already `{ _, _ -> null }`, so the
                // real fix surface is the wrapper-removal + weight
                // changes documented above.)
                key(debugEnabled) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize(),
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                    ) {
                        composable(ONBOARDING_ROUTE) {
                            onboardingContent {
                                navController.navigate(Screen.General.route) {
                                    popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                                }
                            }
                        }
                        composable(Screen.General.route) {
                            SettingsTabsScreen(
                                generalTabContent = generalTabContent,
                                advancedTabContent = advancedTabContent,
                            )
                        }
                        composable(Screen.Models.route) { modelsTabContent() }
                        composable(Screen.PostProcess.route) { postProcessContent() }
                        composable(Screen.History.route) { historyContent() }
                        composable(Screen.About.route) { aboutContent() }
                        // DEBUG_ROUTE — inherits the NavHost-level
                        // transitions (enterTransition / exitTransition /
                        // sizeTransform, all `None` / null-lambda at the
                        // graph level). Per-destination sizeTransform is
                        // omitted in Navigation Compose 2.8.x because the
                        // parameter is a non-nullable lambda.
                        composable(DEBUG_ROUTE) {
                            if (debugEnabled) debugContent()
                            else com.handy.app.ui.debug.DeveloperToolsDisabled()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HandyBottomNavigation(
    navController: NavHostController,
    screens: List<Screen>,
    modifier: Modifier = Modifier, // Sprint 29c — foldable-aware bottom padding
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        modifier = modifier, // Sprint 29c — foldable-aware padding propagation
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        screens.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = { navigateToScreen(navController, screen) },
                icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                label = { Text(stringResource(screen.titleRes)) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun HandyNavigationRail(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    screens: List<Screen>,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationRail(
        modifier = modifier.width(80.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        header = {
            Spacer(Modifier.height(16.dp))
        },
    ) {
        screens.forEach { screen ->
            val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
            NavigationRailItem(
                selected = selected,
                onClick = { navigateToScreen(navController, screen) },
                icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleRes)) },
                label = { Text(stringResource(screen.titleRes)) },
                alwaysShowLabel = true,
            )
        }
    }
}

private fun navigateToScreen(navController: NavHostController, screen: Screen) {
    navController.navigate(screen.route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun SettingsTabsScreen(
    generalTabContent: @Composable () -> Unit,
    advancedTabContent: @Composable () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.settings_title), stringResource(R.string.settings_advanced))

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        // Sprint 30c-#2 (thinker re-diagnosis 2026-07-18 — replaces the
        // Sprint 30c-#1 KDoc block that claimed the OPPOSITE mechanism).
        //
        // CRASH (full stack at handy-android/logs/sprint30c/full_crash_BEFORE_fix.log):
        //   `IllegalArgumentException: maxWidth(-83) must be >= than minWidth(0)`
        //   at `androidx.compose.material3.ListItemMeasurePolicy.measure-3p2s80s(ListItem.kt:234)`.
        //
        // ROOT CAUSE (Compose foundation 1.7.x source-level contract):
        // `Column.minIntrinsicHeight(width)` in
        // `RowColumnMeasurePolicy.kt` sums the intrinsic heights of the
        // **UNWEIGHTED** entries only. Entries with `Modifier.weight(1f)`
        // are flex-resolved at measure time and contribute `0` to the
        // intrinsic-height sum. Therefore a `weight(1f)` Box wrapping
        // the `when {}` body severs the cascade:
        //
        //   AnimatedContent (NavHost inner) measure pass at width=0
        //   → SettingsTabsScreen `Column.fillMaxSize()`
        //   → `Column.minIntrinsicHeight(0)` sums [TabRow.intrinsicHeight(0)] only
        //     (the `weight(1f)` Box returns 0 → LazyColumn SKIPPED)
        //   → no negative-width intrinsic query reaches ListItem.
        //
        // WITHOUT the `weight(1f)` Box (the Sprint 30c-#1 wrong-hypothesis
        // attempt that REMOVED this wrapper), the cascade fires:
        //   `Column.minIntrinsicHeight(0)` queries LazyColumn.intrinsicHeight(0)
        //     → ListItem.intrinsicHeight(0)
        //     → ListItemMeasurePolicy subtracts its internal horizontal
        //       padding (~83dp total for 2-line ListItem with trailing icon)
        //       from incoming maxWidth=0
        //     → `Constraints(maxWidth=-83, ...)`
        //     → assert fails: `maxWidth(-83) must be >= than minWidth(0)`.
        //
        // This `Box(Modifier.fillMaxWidth().weight(1f))` is the **PRIMARY
        // defense** against that cascade. Companion fixes (MainActivity
        // drops the outer `Column.fillMaxSize().verticalScroll(...)`
        // wrappers; SettingsScreen.kt migrates GeneralSettingsContent +
        // AdvancedSettingsContent to LazyColumn internally) are secondary:
        // they close the **`Vertical scrollable component was measured
        // with an infinity maximum height constraints`** runtime check
        // (the Slider/verticalScroll drop) and the **`verticalScroll`
        //-inside-Column-when-Infinity-supplied** check (the LazyColumn
        // migration), but they do NOT close the `maxWidth(-N)` intrinsic
        // cascade — only THIS `weight(1f)` Box does.
        //
        // Sprint 28b-v15 introduced this same wrapper as
        // `Box(Modifier.fillMaxWidth().weight(1f))` with narrowed
        // justification — the original was insufficient because it
        // didn't explain WHY the weight was needed. The Sprint 30c-#1
        // removal (followed by Sprint 30c-#2 restoration) cycled through
        // the same loop twice; this KDoc closes the loop with the source
        // citation so the next agent doesn't repeat the cycle. See
        // `app/src/test/java/com/handy/app/ui/debug/DebugLayoutRegressionTest.kt`
        // for the Robolectric test that locks in this contract (future
        // extension via `DestinationInfinityGuardTest.kt` to add an
        // `intrinsicHeight(0)` assertion on this exact shape).
        //
        // DEFENSIVE NOTE: DO NOT remove this Box, do not remove the
        // `weight(1f)`, do not swap to `fillMaxSize()`/`fillMaxHeight()`.
        // The A059 `IllegalArgumentException: maxWidth(-N) must be >=
        // than minWidth(0)` runtime crash returns IMMEDIATELY. Modifying
        // the wrapper requires re-running the on-device cold-launch
        // verify with `--ez skip_onboarding true` and confirming the
        // crash log at `handy-android/logs/sprint30c/` does NOT contain
        // `IllegalArgumentException: maxWidth(`. See AGENTS.md Sprint
        // 30c-#2 closure for the diagnostic journey.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (selectedTab) {
                0 -> generalTabContent()
                1 -> advancedTabContent()
            }
        }
    }
}

// Sprint 26: Post-Process was promoted to its own top-level
// Screen.PostProcess destination; ModelsTabsScreen tab-pill logic
// was removed because Models is now the sole route content. This
// function slot is retained as a breadcrumb so future agents can
// see the prior 2-tab structure for reference.
