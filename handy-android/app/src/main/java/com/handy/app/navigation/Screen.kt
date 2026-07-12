package com.handy.app.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dictation : Screen("dictation")
    data object Models : Screen("models")
    data object Settings : Screen("settings")
    data object History : Screen("history")
}
