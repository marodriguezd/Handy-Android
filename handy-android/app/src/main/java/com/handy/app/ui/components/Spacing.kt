package com.handy.app.ui.components

import androidx.compose.ui.unit.dp

/**
 * MD3 8 dp spacing grid tokens.  Replaces ad-hoc `.dp` magic numbers across
 * the codebase so spacing stays consistent with the design system.  Use
 * [Spacing.lg] (16) for default content padding,
 * [Spacing.md] (12) for tight rows,
 * [Spacing.sm] (8) for compact gaps.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp
}
