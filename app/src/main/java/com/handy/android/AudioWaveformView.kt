package com.handy.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/** Small, dependency-free waveform indicator for the floating recording control with MD3 color binding and exponential decay smoothing. */
class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    // Initial fallback for a plain View without theme access; the host service always calls
    // setWaveformColor() with the theme colour right after creating this view.
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var currentAmplitude = 0f
    private var targetAmplitude = 0f

    fun setWaveformColor(color: Int) {
        paint.color = color
        postInvalidateOnAnimation()
    }

    fun setAmplitude(value: Float) {
        targetAmplitude = value.coerceIn(0f, 1f)
        // Rapid response on peak increase, exponential decay interpolation on decrease
        val alpha = if (targetAmplitude > currentAmplitude) 0.6f else 0.25f
        currentAmplitude += (targetAmplitude - currentAmplitude) * alpha
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Smooth exponential decay interpolation towards target amplitude
        if (abs(targetAmplitude - currentAmplitude) > 0.002f) {
            val alpha = if (targetAmplitude > currentAmplitude) 0.4f else 0.18f
            currentAmplitude += (targetAmplitude - currentAmplitude) * alpha
            postInvalidateOnAnimation()
        } else {
            currentAmplitude = targetAmplitude
        }

        val center = height / 2f
        val barWidth = width / (BAR_COUNT * 2f)
        val gap = barWidth
        repeat(BAR_COUNT) { index ->
            val distance = abs(index - (BAR_COUNT - 1) / 2f) / ((BAR_COUNT - 1) / 2f)
            val heightRatio = max(0.15f, currentAmplitude * (1f - distance * 0.65f))
            val barHeight = height * 0.8f * heightRatio
            val left = index * (barWidth + gap) + gap / 2f
            canvas.drawRoundRect(left, center - barHeight / 2f, left + barWidth, center + barHeight / 2f, barWidth, barWidth, paint)
        }
    }

    companion object {
        private const val BAR_COUNT = 7
    }
}
