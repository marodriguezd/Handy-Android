package com.handy.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioWaveformViewTest {

    @Test
    fun testSetAmplitudeNormalRange() {
        val view = AudioWaveformView(ApplicationProvider.getApplicationContext())
        view.setAmplitude(0.0f)
        view.setAmplitude(0.5f)
        view.setAmplitude(1.0f)
    }

    @Test
    fun testSetAmplitudeExtremeValues() {
        val view = AudioWaveformView(ApplicationProvider.getApplicationContext())
        // Negative values should be clamped safely to 0f
        view.setAmplitude(-1.0f)
        view.setAmplitude(-100.0f)

        // Overflow values should be clamped safely to 1f
        view.setAmplitude(2.0f)
        view.setAmplitude(1000.0f)
        view.setAmplitude(Float.MAX_VALUE)
    }

    @Test
    fun testSetAmplitudeNaNAndInfinity() {
        val view = AudioWaveformView(ApplicationProvider.getApplicationContext())
        // NaN and Infinity values
        view.setAmplitude(Float.NaN)
        view.setAmplitude(Float.POSITIVE_INFINITY)
        view.setAmplitude(Float.NEGATIVE_INFINITY)
    }

    @Test
    fun testRapidAmplitudeBursts() {
        val view = AudioWaveformView(ApplicationProvider.getApplicationContext())
        // Rapid alternating amplitude bursts (e.g. 10,000 iterations)
        for (i in 0 until 10_000) {
            val amp = if (i % 2 == 0) 1.0f else 0.0f
            view.setAmplitude(amp)
        }
    }

    @Test
    fun testSetWaveformColor() {
        val view = AudioWaveformView(ApplicationProvider.getApplicationContext())
        view.setWaveformColor(Color.RED)
        view.setWaveformColor(Color.TRANSPARENT)
        view.setWaveformColor(Color.argb(255, 208, 188, 255))
    }

    @Test
    fun testOnDrawRenderingWithoutCrash() {
        val view = AudioWaveformView(ApplicationProvider.getApplicationContext())
        view.layout(0, 0, 200, 100)
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw idle state
        view.setAmplitude(0f)
        view.draw(canvas)

        // Draw peak state
        view.setAmplitude(1.0f)
        view.draw(canvas)

        // Draw multiple frames of decay animation
        for (i in 0..10) {
            view.draw(canvas)
        }
    }
}
