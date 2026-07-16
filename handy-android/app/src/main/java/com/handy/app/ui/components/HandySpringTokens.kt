package com.handy.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * MD3-native spring specs for finite UI states (press-scale,
 * confirm-bar exits, animated values that settle onto a target).
 *
 * Compose M3 motion canon uses spring physics for natural feedback:
 * `stiffness` controls the rate of approach, `dampingRatio` controls
 * oscillation.  Use [gentle] for subtle UI bounces, [bouncy] for
 * attention-pulling morphology changes (waveform bars, large-scale
 * state transitions).
 *
 * **Important caveat**: `infiniteRepeatable { spring(...) }` does NOT
 * compile — Compose's `infiniteRepeatable` only accepts a
 * `DurationBasedAnimationSpec`.  Infinite-loop animations (the
 * recording pulse dot, the always-cycling waveform) keep using `tween`
 * with `RepeatMode.Reverse`.  Spring physics on an infinite loop is
 * approximated by toggling a Boolean state with `LaunchedEffect` and
 * binding `animateFloatAsState` to it.
 */
object HandySpringTokens {

    /** Gentle spring — for press-scale, card-elevation lifts, confirm exits. */
    fun <T> gentle(): SpringSpec<T> = spring(
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = 0.85f,
    )

    /** Bouncy spring — for waveform bars, recording-pulse dot, big state swaps. */
    fun <T> bouncy(): SpringSpec<T> = spring(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = 0.65f,
    )

    /** Snappy spring — for very fast tap acknowledgement. */
    fun <T> snappy(): SpringSpec<T> = spring(
        stiffness = Spring.StiffnessHigh,
        dampingRatio = 0.9f,
    )
}
