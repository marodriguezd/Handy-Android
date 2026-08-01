package com.handy.android

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.ConcurrentHashMap

internal enum class AudioFeedbackEvent {
    START_RECORDING,
    STOP_RECORDING,
    TRANSCRIPTION_SUCCESS,
}

internal fun interface SoundFeedbackPlayer {
    fun play(event: AudioFeedbackEvent)
}

internal fun interface HapticFeedbackPlayer {
    fun play(event: AudioFeedbackEvent)
}

private interface ReleasableFeedbackPlayer {
    fun release()
}

/** Coordinates optional audio and haptic signals for recording workflows. */
internal class AudioFeedbackController(
    private val soundPlayer: SoundFeedbackPlayer,
    private val hapticPlayer: HapticFeedbackPlayer,
) {
    fun onStartRecording(context: Context) = emit(context, AudioFeedbackEvent.START_RECORDING)

    fun onStopRecording(context: Context) = emit(context, AudioFeedbackEvent.STOP_RECORDING)

    fun onTranscriptionSuccess(context: Context) = emit(context, AudioFeedbackEvent.TRANSCRIPTION_SUCCESS)

    fun release() {
        (soundPlayer as? ReleasableFeedbackPlayer)?.release()
        (hapticPlayer as? ReleasableFeedbackPlayer)?.release()
    }

    private fun emit(context: Context, event: AudioFeedbackEvent) {
        if (SettingsManager.soundFeedbackEnabled(context)) soundPlayer.play(event)
        if (SettingsManager.hapticFeedbackEnabled(context)) hapticPlayer.play(event)
    }
}

/** Process-wide low-latency feedback manager shared by activities and services. */
object AudioFeedbackManager {
    private val lock = Any()
    private var controller: AudioFeedbackController? = null

    fun onStartRecording(context: Context) = controller(context).onStartRecording(context)

    fun onStopRecording(context: Context) = controller(context).onStopRecording(context)

    fun onTranscriptionSuccess(context: Context) = controller(context).onTranscriptionSuccess(context)

    /** Releases native audio resources; the manager will lazily recreate them on demand. */
    fun release() = synchronized(lock) {
        controller?.release()
        controller = null
    }

    private fun controller(context: Context): AudioFeedbackController = synchronized(lock) {
        controller ?: AudioFeedbackController(
            soundPlayer = SoundPoolFeedbackPlayer(context.applicationContext),
            hapticPlayer = VibratorFeedbackPlayer(context.applicationContext),
        ).also { controller = it }
    }
}

private class SoundPoolFeedbackPlayer(private val context: Context) : SoundFeedbackPlayer, ReleasableFeedbackPlayer {
    private val audioLock = Any()
    private val soundPool: SoundPool?
    private val loaded = ConcurrentHashMap<Int, Boolean>()
    private val soundIds: Map<AudioFeedbackEvent, Int>
    private var toneGenerator: ToneGenerator? = null

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = runCatching {
            SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(attributes)
                .build()
        }.getOrNull()
        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            loaded[sampleId] = status == 0
        }
        soundIds = if (soundPool != null) {
            mapOf(
                AudioFeedbackEvent.START_RECORDING to load(R.raw.record_start),
                AudioFeedbackEvent.STOP_RECORDING to load(R.raw.record_stop),
                AudioFeedbackEvent.TRANSCRIPTION_SUCCESS to load(R.raw.transcribe_success),
            )
        } else {
            emptyMap()
        }
    }

    private fun load(resourceId: Int): Int = runCatching { soundPool?.load(context, resourceId, 1) ?: 0 }.getOrDefault(0)

    override fun play(event: AudioFeedbackEvent) {
        val sampleId = soundIds[event] ?: 0
        val played = synchronized(audioLock) {
            if (sampleId == 0 || loaded[sampleId] != true) {
                false
            } else {
                runCatching { soundPool?.play(sampleId, 1f, 1f, 1, 0, 1f) ?: 0 }
                    .getOrDefault(0) != 0
            }
        }
        if (!played) playFallbackTone(event)
    }

    private fun playFallbackTone(event: AudioFeedbackEvent) {
        val tone = when (event) {
            AudioFeedbackEvent.START_RECORDING -> ToneGenerator.TONE_PROP_BEEP
            AudioFeedbackEvent.STOP_RECORDING -> ToneGenerator.TONE_PROP_ACK
            AudioFeedbackEvent.TRANSCRIPTION_SUCCESS -> ToneGenerator.TONE_PROP_PROMPT
        }
        synchronized(audioLock) {
            if (toneGenerator == null) {
                toneGenerator = runCatching {
                    ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
                }.getOrNull()
            }
            runCatching { toneGenerator?.startTone(tone, 100) }
        }
    }

    override fun release() = synchronized(audioLock) {
        soundPool?.release()
        toneGenerator?.release()
        toneGenerator = null
    }
}

private class VibratorFeedbackPlayer(private val context: Context) : HapticFeedbackPlayer {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }

    override fun play(event: AudioFeedbackEvent) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        val effect = when {
            event == AudioFeedbackEvent.TRANSCRIPTION_SUCCESS ->
                VibrationEffect.createWaveform(longArrayOf(0, 30), -1)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                VibrationEffect.createPredefined(
                    if (event == AudioFeedbackEvent.START_RECORDING) {
                        VibrationEffect.EFFECT_CLICK
                    } else {
                        VibrationEffect.EFFECT_DOUBLE_CLICK
                    },
                )
            else -> {
                @Suppress("DEPRECATION")
                VibrationEffect.createWaveform(
                    if (event == AudioFeedbackEvent.START_RECORDING) longArrayOf(0, 20) else longArrayOf(0, 20, 40, 20),
                    -1,
                )
            }
        }
        runCatching { device.vibrate(effect) }
    }
}
