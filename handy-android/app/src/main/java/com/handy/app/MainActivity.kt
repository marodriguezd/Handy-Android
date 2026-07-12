package com.handy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handy.app.di.ViewModelFactory
import com.handy.app.navigation.AppNavigation
import com.handy.app.ui.dictation.DictationScreen
import com.handy.app.ui.history.HistoryScreen
import com.handy.app.ui.models.ModelCatalogScreen
import com.handy.app.ui.onboarding.OnboardingScreen
import com.handy.app.ui.settings.SettingsScreen
import com.handy.app.ui.theme.HandyTheme
import com.handy.app.viewmodel.HistoryViewModel
import com.handy.app.viewmodel.ModelsViewModel
import com.handy.app.viewmodel.OnboardingViewModel
import com.handy.app.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val app = remember { (application as HandyApplication) }

            HandyTheme {
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
                    dictationContent = {
                        DictationScreen(engineViewModel = app.engineViewModel)
                    },
                    modelsContent = {
                        val vm: ModelsViewModel = viewModel(
                            factory = ViewModelFactory.create(app)
                        )
                        ModelCatalogScreen(viewModel = vm)
                    },
                    settingsContent = {
                        val vm: SettingsViewModel = viewModel(
                            factory = ViewModelFactory.create(app)
                        )
                        SettingsScreen(viewModel = vm)
                    },
                    historyContent = {
                        val vm: HistoryViewModel = viewModel(
                            factory = ViewModelFactory.create(app)
                        )
                        HistoryScreen(viewModel = vm)
                    },
                )
            }
        }
    }
}
