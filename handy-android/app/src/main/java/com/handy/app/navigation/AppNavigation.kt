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
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
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
        // Sprint 28b-v15 — wrap the `when` body in `Box(Modifier.weight(1f))`
        // so the active tab body receives bounded `maxHeight`. Without the
        // weight, the parent Column passes `Constraints.Infinity` for
        // `maxHeight` to whichever composable the `when` selects — and
        // MainActivity wraps both `generalTabContent` and
        // `advancedTabContent` in their own `Modifier.verticalScroll(...)`,
        // making those lambdas the runtime-check target on tab navigation.
        // With `weight(1f)`, the Box receives the remaining column height
        // (= parent - TabRow) as `maxHeight` and any inner verticalScroll
        // is bounded. Defense-in-depth for the Settings→General and
        // Settings→Advanced routes.
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
