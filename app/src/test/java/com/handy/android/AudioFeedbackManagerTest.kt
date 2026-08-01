package com.handy.android

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFeedbackManagerTest {
    private val context = FileBackedContext()

    @After
    fun cleanUp() {
        context.root.deleteRecursively()
    }

    @Test
    fun feedbackPreferencesAreEnabledByDefaultAndPersistIndependently() {
        assertTrue(SettingsManager.soundFeedbackEnabled(context))
        assertTrue(SettingsManager.hapticFeedbackEnabled(context))

        SettingsManager.setSoundFeedbackEnabled(context, false)
        SettingsManager.setHapticFeedbackEnabled(context, true)

        assertEquals(false, SettingsManager.soundFeedbackEnabled(context))
        assertEquals(true, SettingsManager.hapticFeedbackEnabled(context))

        SettingsManager.setSoundFeedbackEnabled(context, true)
        SettingsManager.setHapticFeedbackEnabled(context, false)

        assertEquals(true, SettingsManager.soundFeedbackEnabled(context))
        assertEquals(false, SettingsManager.hapticFeedbackEnabled(context))
    }

    @Test
    fun controllerRoutesEachEventOnlyToEnabledFeedbackChannels() {
        val soundEvents = mutableListOf<AudioFeedbackEvent>()
        val hapticEvents = mutableListOf<AudioFeedbackEvent>()
        val controller = AudioFeedbackController(
            soundPlayer = SoundFeedbackPlayer { soundEvents += it },
            hapticPlayer = HapticFeedbackPlayer { hapticEvents += it },
        )

        SettingsManager.setSoundFeedbackEnabled(context, true)
        SettingsManager.setHapticFeedbackEnabled(context, false)
        controller.onStartRecording(context)
        controller.onStopRecording(context)
        controller.onTranscriptionSuccess(context)

        assertEquals(
            listOf(
                AudioFeedbackEvent.START_RECORDING,
                AudioFeedbackEvent.STOP_RECORDING,
                AudioFeedbackEvent.TRANSCRIPTION_SUCCESS,
            ),
            soundEvents,
        )
        assertTrue(hapticEvents.isEmpty())

        SettingsManager.setSoundFeedbackEnabled(context, false)
        SettingsManager.setHapticFeedbackEnabled(context, true)
        controller.onTranscriptionSuccess(context)

        assertEquals(3, soundEvents.size)
        assertEquals(listOf(AudioFeedbackEvent.TRANSCRIPTION_SUCCESS), hapticEvents)
    }

    private class FileBackedContext : ContextWrapper(null) {
        val root: File = Files.createTempDirectory("handy-feedback-test").toFile()

        override fun getFilesDir(): File = root

        override fun getApplicationContext(): Context = this
    }
}
