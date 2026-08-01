package com.handy.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RecognizeActivity : ComponentActivity() {
    private lateinit var recorder: AudioRecorder
    private var recording = false
    private var started = false
    private var status by mutableStateOf("Ready")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recorder = AudioRecorder(this)
        setContent {
            MaterialTheme {
                val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) startRecording() else finishCanceled()
                }
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Handy voice input", style = MaterialTheme.typography.headlineSmall)
                    Text(status, modifier = Modifier.padding(vertical = 16.dp))
                    if (recording) CircularProgressIndicator()
                    Button(onClick = {
                        if (recording) stopRecording() else if (hasMicrophonePermission()) startRecording() else requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text(if (recording) "Stop" else "Record") }
                }
                if (!started && intent?.action == RecognizerIntent.ACTION_RECOGNIZE_SPEECH) {
                    started = true
                    if (hasMicrophonePermission()) startRecording() else requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    private fun startRecording() {
        if (recording || isFinishing) return
        if (!recorder.start()) {
            status = "Microphone unavailable"
            return
        }
        recording = true
        AudioFeedbackManager.onStartRecording(this)
        status = "Listening…"
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        AudioFeedbackManager.onStopRecording(this)
        status = "Transcribing…"
        val samples = recorder.stop()
        val audioPath = runCatching { AudioRecorder.writeWav(this@RecognizeActivity, samples).absolutePath }.getOrNull()
        lifecycleScope.launch {
            try {
                val text = TranscriptionEngine.transcribe(this@RecognizeActivity, samples)
                if (text.isBlank()) {
                    finishCanceled()
                } else {
                    AudioFeedbackManager.onTranscriptionSuccess(this@RecognizeActivity)
                    HistoryRepository.record(
                        context = this@RecognizeActivity,
                        text = text,
                        sourceType = HistorySource.VOICE_INPUT,
                        durationMs = AudioRecorder.durationMs(samples),
                        audioFilePath = audioPath,
                    )
                    finishWithResult(text)
                }
            } catch (error: Exception) {
                status = error.message ?: "Transcription failed"
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing && recording) {
            recording = false
            AudioFeedbackManager.onStopRecording(this)
            recorder.stop()
            finishCanceled()
        }
        super.onStop()
    }

    override fun onDestroy() {
        recorder.release()
        super.onDestroy()
    }

    private fun finishWithResult(text: String) {
        setResult(RESULT_OK, Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(text)))
        finish()
    }

    private fun finishCanceled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun hasMicrophonePermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

}
