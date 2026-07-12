package com.handy.app.injection

interface InjectorStrategy {
    val displayName: String
    fun isAvailable(): Boolean
    suspend fun inject(text: String): Result<Unit>
}
