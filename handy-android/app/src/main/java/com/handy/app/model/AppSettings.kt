package com.handy.app.model

data class AppSettings(
    val idleTimeout: Int = 30,
    val shizukuEnabled: Boolean = false,
    val postProcessEndpoint: String = "",
    val postProcessApiKey: String = "",
    val batteryOptimizationExempt: Boolean = false,
    val experimentalEnabled: Boolean = false,
    val vadEnabled: Boolean = true,
    val addFinalSpace: Boolean = false,
    val postProcessingEnabled: Boolean = true,
    val autoSend: String = "disabled",
)
