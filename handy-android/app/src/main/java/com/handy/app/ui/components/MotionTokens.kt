package com.handy.app.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * MD3 motion tokens derived from https://m3.material.io/styles/motion/easing-and-duration/tokens.
 *
 *  - **Standard** easings/durations are utility motion (chips, switches).
 *  - **Emphasized** is for hero transitions (modal screens, the IME
 *    state machine, screen-to-screen navigation).
 *  - **Springs** are MD3 Expressive-friendly replacements for
 *    tween/curve combos where a discrete duration doesn't capture the
 *    incoming motion's intent (e.g. waveform VAD bars).
 */
object MotionTokens {
    // PC overlay-aligned easings (the PC overlay pop also uses (0.22, 1, 0.36, 1)).
    val PopEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    // MD3 standardized easing curves.
    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)
    val StandardAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // Durations in ms (mapped to Int because `tween(ms)` accepts Int).
    const val DurationShort = 150
    const val DurationMedium = 300
    const val DurationLong = 500

    /**
     * Spring specs selected to match the PC overlay's spring physics.
     * Use [Spring.SoftnessLow] for pills, [Spring.Medium] for content
     * panels.
     */
    fun softSpring(): SpringSpec<Float> = spring(
        stiffness = Spring.StiffnessLow,
        dampingRatio = Spring.DampingRatioLowBouncy,
    )

    fun interactiveSpring(): SpringSpec<Float> = spring(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioMediumBouncy,
    )
}
