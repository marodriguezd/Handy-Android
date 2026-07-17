package com.handy.app.viewmodel

import com.handy.app.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel.UiState.toAppSettings] pure mapping.
 *
 * Coverage:
 *  - Test 1 — Default [SettingsViewModel.UiState] round-trips every persisted
 *    field into [AppSettings]. (Field-level identity, no structural exclusion
 *    assertion.)
 *  - Test 2 — Custom [SettingsViewModel.UiState] with all 11 fields
 *    (including `isApiKeyVisible=true`) survives the round-trip AND a
 *    Java-reflection check confirms `isApiKeyVisible` is NOT a parameter
 *    on [AppSettings], proving the UI-only flag cannot leak into the engine
 *    layer no matter what the UI emits.
 */
class SettingsViewModelUiStateTest {

    @Test
    fun `default UiState round-trips every persisted field identity into AppSettings`() {
        val ui = SettingsViewModel.UiState()

        val mapped = ui.toAppSettings()

        assertEquals(ui.idleTimeout, mapped.idleTimeout)
        assertEquals(ui.shizukuEnabled, mapped.shizukuEnabled)
        assertEquals(ui.postProcessEndpoint, mapped.postProcessEndpoint)
        assertEquals(ui.postProcessApiKey, mapped.postProcessApiKey)
        assertEquals(ui.batteryOptimizationExempt, mapped.batteryOptimizationExempt)
        assertEquals(ui.experimentalEnabled, mapped.experimentalEnabled)
        assertEquals(ui.vadEnabled, mapped.vadEnabled)
        assertEquals(ui.addFinalSpace, mapped.addFinalSpace)
        assertEquals(ui.postProcessingEnabled, mapped.postProcessingEnabled)
        assertEquals(ui.autoSend, mapped.autoSend)
    }

    @Test
    fun `custom UiState round-trips postProcessApiKey, isApiKeyVisible flag is structurally excluded`() {
        val ui = SettingsViewModel.UiState(
            idleTimeout = 90,
            shizukuEnabled = true,
            postProcessEndpoint = "http://10.0.2.2:11434",
            postProcessApiKey = "sk-test-abcdef1234",
            // UI-only flag toggled to true — must NOT bleed into AppSettings.
            isApiKeyVisible = true,
            batteryOptimizationExempt = true,
            experimentalEnabled = true,
            vadEnabled = false,
            addFinalSpace = true,
            postProcessingEnabled = false,
            autoSend = "always",
        )

        val mapped = ui.toAppSettings()

        assertEquals(90, mapped.idleTimeout)
        assertEquals(true, mapped.shizukuEnabled)
        assertEquals("http://10.0.2.2:11434", mapped.postProcessEndpoint)
        assertEquals("sk-test-abcdef1234", mapped.postProcessApiKey)
        assertEquals(true, mapped.batteryOptimizationExempt)
        assertEquals(true, mapped.experimentalEnabled)
        assertEquals(false, mapped.vadEnabled)
        assertEquals(true, mapped.addFinalSpace)
        assertEquals(false, mapped.postProcessingEnabled)
        assertEquals("always", mapped.autoSend)

        // Belt-and-suspenders structural check: AppSettings has exactly the 10
        // engineered-engine fields and zero UI-only isApiKeyVisible. We reach
        // into the constructor parameter names via Java reflection so the test
        // does not pull in kotlin-reflect (which is not currently on the
        // testImplementation classpath).
        val tenParamCtor = AppSettings::class.java.declaredConstructors
            .firstOrNull { it.parameterCount == 10 }
            ?: error("AppSettings must have a 10-parameter primary constructor")
        val paramNames = tenParamCtor.parameters.map { it.name }.toSet()
        assertFalse(
            "`isApiKeyVisible` must NOT appear as an AppSettings constructor param",
            paramNames.contains("isApiKeyVisible"),
        )
    }
}
