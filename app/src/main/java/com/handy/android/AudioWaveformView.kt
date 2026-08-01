package com.handy.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.max

/** Small, dependency-free waveform indicator for the floating recording control. */
class AudioWaveformView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var amplitude = 0f

    fun setAmplitude(value: Float) {
        amplitude = value.coerceIn(0f, 1f)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val center = height / 2f
        val barWidth = width / (BAR_COUNT * 2f)
        val gap = barWidth
        repeat(BAR_COUNT) { index ->
            val distance = kotlin.math.abs(index - (BAR_COUNT - 1) / 2f) / ((BAR_COUNT - 1) / 2f)
            val heightRatio = max(0.15f, amplitude * (1f - distance * 0.65f))
            val barHeight = height * 0.8f * heightRatio
            val left = index * (barWidth + gap) + gap / 2f
            canvas.drawRoundRect(left, center - barHeight / 2f, left + barWidth, center + barHeight / 2f, barWidth, barWidth, paint)
        }
    }

    companion object {
        private const val BAR_COUNT = 7
    }
}
