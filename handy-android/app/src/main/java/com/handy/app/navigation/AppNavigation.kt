package com.handy.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private const val ONBOARDING_ROUTE = "onboarding"

private val NavScreens = listOf(
    Screen.General,
    Screen.Models,
    Screen.History,
    Screen.About,
)

private enum class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    General("general", R.string.settings_title, Icons.Default.Settings),
    Models("models", R.string.tab_models, Icons.Default.Build),
    History("history", R.string.history_title, Icons.Default.History),
    About("about", R.string.settings_about, Icons.Default.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    onboardingCompleted: Boolean,
    onboardingContent: @Composable (onComplete: () -> Unit) -> Unit,
    generalTabContent: @Composable () -> Unit,
    advancedTabContent: @Composable () -> Unit,
    modelsTabContent: @Composable () -> Unit,
    postProcessTabContent: @Composable () -> Unit,
    historyContent: @Composable () -> Unit,
    aboutContent: @Composable () -> Unit,
) {
    val navController = rememberNavController()
    val startDestination = if (onboardingCompleted) Screen.General.route else ONBOARDING_ROUTE

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentScreen = NavScreens.find { it.route == currentDestination?.route }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isCompact = screenWidthDp < 600

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
                HandyBottomNavigation(navController = navController)
            }
        },
    ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize()) {
            if (!isCompact && currentDestination?.route != ONBOARDING_ROUTE) {
                HandyNavigationRail(
                    navController = navController,
                    modifier = Modifier.fillMaxHeight(),
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
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
                    composable(Screen.Models.route) {
                        ModelsTabsScreen(
                            modelsTabContent = modelsTabContent,
                            postProcessTabContent = postProcessTabContent,
                        )
                    }
                    composable(Screen.History.route) { historyContent() }
                    composable(Screen.About.route) { aboutContent() }
                }
            }
        }
    }
}

@Composable
private fun HandyBottomNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        NavScreens.forEach { screen ->
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
        NavScreens.forEach { screen ->
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
        when (selectedTab) {
            0 -> generalTabContent()
            1 -> advancedTabContent()
        }
    }
}

@Composable
private fun ModelsTabsScreen(
    modelsTabContent: @Composable () -> Unit,
    postProcessTabContent: @Composable () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.tab_models), stringResource(R.string.tab_post_process))

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
        when (selectedTab) {
            0 -> modelsTabContent()
            1 -> postProcessTabContent()
        }
    }
}
