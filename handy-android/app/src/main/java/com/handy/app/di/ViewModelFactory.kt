package com.handy.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.handy.app.HandyApplication
import com.handy.app.viewmodel.HistoryViewModel
import com.handy.app.viewmodel.ModelsViewModel
import com.handy.app.viewmodel.OnboardingViewModel
import com.handy.app.viewmodel.SettingsViewModel

object ViewModelFactory {
    fun create(app: HandyApplication): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(ModelsViewModel::class.java) ->
                        ModelsViewModel(app) as T
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                        SettingsViewModel(app, app.settingsStore, app.engineViewModel) as T
                    modelClass.isAssignableFrom(HistoryViewModel::class.java) ->
                        HistoryViewModel(app) as T
                    modelClass.isAssignableFrom(OnboardingViewModel::class.java) ->
                        OnboardingViewModel(
                            settingsStore = app.settingsStore,
                            modelsViewModelFactory = { ModelsViewModel(app) },
                            app = app,
                        ) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
        }
    }
}
