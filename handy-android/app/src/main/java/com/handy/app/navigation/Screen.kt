package com.handy.app.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object General : Screen("general")
    data object Models : Screen("models")
    data object History : Screen("history")
    data object About : Screen("about")
}
