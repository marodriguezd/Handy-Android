package com.handy.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * MD3-native [Snackbar] wrapper.  Surfaces global errors (paste failures,
 * transcription failures, model load failures) from the Kotlin/Rust
 * event bus.  Use [HandySnackbarHost] inside any Composable that has a
 * `Box` or `Scaffold` — the host observes [HandySnackbarHostState] that
 * you push to from elsewhere with [showSnackbar].
 */
@Composable
fun HandySnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    Snackbar(
        snackbarData = snackbarData,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        actionColor = MaterialTheme.colorScheme.inversePrimary,
    )
}

@Composable
fun HandySnackbarHost(
    state: HandySnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = state.delegate,
        modifier = modifier,
    ) { data -> HandySnackbar(snackbarData = data) }
}

/**
 * Compose-friendly snackbar host state.  Owns a [SnackbarHostState]
 * internally so callers don't have to worry about snackbar API changes.
 *
 * Usage:
 * ```
 * val snackbar = remember { HandySnackbarHostState() }
 * LaunchedEffect(Unit) {
 *     snackbar.showMessage("Recording failed")
 * }
 * HandySnackbarHost(state = snackbar)
 * ```
 */
class HandySnackbarHostState {
    val delegate: SnackbarHostState = SnackbarHostState()

    suspend fun showMessage(message: String, duration: SnackbarDuration = SnackbarDuration.Short) {
        delegate.currentSnackbarData?.dismiss()
        delegate.showSnackbar(message = message, duration = duration)
    }

    suspend fun showError(message: String) {
        delegate.currentSnackbarData?.dismiss()
        delegate.showSnackbar(
            message = message,
            duration = SnackbarDuration.Long,
        )
    }
}

/**
 * Convenience composition for pushing a single message on first effect.
 * Use when the consumer is wired via `LaunchedEffect(...)` once.
 */
@Composable
fun ShowSnackbarOnce(
    state: HandySnackbarHostState,
    message: String?,
) {
    val pending = remember(message) { message }
    LaunchedEffect(pending) {
        if (!pending.isNullOrBlank()) state.showMessage(pending)
    }
}
