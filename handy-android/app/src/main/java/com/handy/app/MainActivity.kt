package com.handy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.handy.app.di.ViewModelFactory
import com.handy.app.navigation.AppNavigation
import com.handy.app.ui.history.HistoryScreen
import com.handy.app.ui.models.ModelCatalogScreen
import com.handy.app.ui.onboarding.OnboardingScreen
import com.handy.app.ui.settings.AboutContent
import com.handy.app.ui.settings.AdvancedSettingsContent
import com.handy.app.ui.settings.GeneralSettingsContent
import com.handy.app.ui.settings.PostProcessContent
import com.handy.app.ui.theme.HandyTheme
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

        val app = (application as HandyApplication)
        if (intent?.getBooleanExtra("skip_onboarding", false) == true) {
            app.settingsStore.onboardingCompleted = true
        }

        if (intent?.getBooleanExtra("start_dictation", false) == true) {
            app.engineViewModel.startRecording()
        }

        setContent {
            val app = remember { (application as HandyApplication) }
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = ViewModelFactory.create(app)
            )

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
                    postProcessTabContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            PostProcessContent(viewModel = settingsViewModel)
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
        if (savedIsDictating) {
            android.util.Log.w("HandyMain", "Recording state lost during config change")
        }
    }
}
