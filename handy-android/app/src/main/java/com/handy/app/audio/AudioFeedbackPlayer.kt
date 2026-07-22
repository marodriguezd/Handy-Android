package com.handy.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.handy.app.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

enum class SoundEvent {
    START,
    STOP,
    ERROR
}

/**
 * AudioFeedbackPlayer handles playing audio feedback sound effects (Start, Stop, Error)
 * matching the PC version's audio_feedback.rs module.
 */
class AudioFeedbackPlayer(private val context: Context) {

    private val settingsStore = SettingsStore(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun playSound(event: SoundEvent) {
        if (!settingsStore.audioFeedbackEnabled) return

        val volume = (settingsStore.audioFeedbackVolume / 100f).coerceIn(0f, 1f)
        if (volume <= 0f) return

        scope.launch {
            try {
                when (event) {
                    SoundEvent.START -> playTone(880f, 440f, 120, volume) // A5 -> A4 chime
                    SoundEvent.STOP -> playTone(440f, 880f, 120, volume)  // A4 -> A5 chime
                    SoundEvent.ERROR -> playTone(220f, 180f, 250, volume) // Low warning
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to play audio feedback sound", e)
            }
        }
    }

    private fun playTone(freqStart: Float, freqEnd: Float, durationMs: Int, volume: Float) {
        val sampleRate = 44100
        val numSamples = (durationMs * sampleRate / 1000)
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toFloat() / numSamples
            val currentFreq = freqStart + (freqEnd - freqStart) * t
            val angle = 2.0 * Math.PI * i * currentFreq / sampleRate
            val sample = (sin(angle) * Short.MAX_VALUE * volume).toInt()
            buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()

        // Release AudioTrack after playback duration
        scope.launch {
            kotlinx.coroutines.delay(durationMs.toLong() + 50)
            audioTrack.release()
        }
    }

    companion object {
        private const val TAG = "AudioFeedbackPlayer"
    }
}
