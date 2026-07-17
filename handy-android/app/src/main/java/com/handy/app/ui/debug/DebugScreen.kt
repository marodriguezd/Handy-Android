package com.handy.app.ui.debug

import androidx.compose.runtime.Composable

/**
 * Sprint 28 — minimal Debug screen wrapper.
 *
 * Renders the MD3 [DebugContent] composition root. A future Sprint 28b
 * may introduce a `DebugViewModel` for state (LogLevel / ring-buffer
 * tape); for now the screen is purely presentational so the gated
 * route is reachable and verifiable.
 */
@Composable
fun DebugScreen() {
    DebugContent()
}
