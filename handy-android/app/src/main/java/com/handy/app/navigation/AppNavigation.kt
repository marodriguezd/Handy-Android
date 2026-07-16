package com.handy.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import com.handy.app.R
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
)

@Composable
private fun getBottomNavItems(): List<BottomNavItem> {
    return listOf(
        BottomNavItem(stringResource(R.string.settings_title), Icons.Default.Settings, Screen.General),
        BottomNavItem(stringResource(R.string.tab_models), Icons.Default.Build, Screen.Models),
        BottomNavItem(stringResource(R.string.history_title), Icons.Default.DateRange, Screen.History),
        BottomNavItem(stringResource(R.string.settings_about), Icons.Default.Info, Screen.About),
    )
}

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
    val startDestination = if (onboardingCompleted) Screen.General.route else Screen.Onboarding.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route != Screen.Onboarding.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val navItems = getBottomNavItems()
                    navItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateToTab(navController, item.screen) },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
        ) {
            composable(Screen.Onboarding.route) {
                onboardingContent {
                    navController.navigate(Screen.General.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            }
            composable(Screen.General.route) {
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
            composable(Screen.Models.route) {
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
            composable(Screen.History.route) { historyContent() }
            composable(Screen.About.route) { aboutContent() }
        }
    }
}

private fun navigateToTab(navController: NavHostController, screen: Screen) {
    navController.navigate(screen.route) {
        popUpTo(navController.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
