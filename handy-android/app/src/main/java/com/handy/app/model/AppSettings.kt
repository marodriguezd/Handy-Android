package com.handy.app.model

data class AppSettings(
    val idleTimeout: Int = 30,
    val shizukuEnabled: Boolean = false,
    val postProcessEndpoint: String = "",
    val postProcessApiKey: String = "",
    val batteryOptimizationExempt: Boolean = false,
)
