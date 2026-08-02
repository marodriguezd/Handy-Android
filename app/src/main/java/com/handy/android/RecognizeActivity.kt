package com.handy.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.handy.android.ui.theme.HandyTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class RecognizeActivity : ComponentActivity() {
    private lateinit var recorder: AudioRecorder
    private var recording = false
    private var started = false
    private var status by mutableStateOf("")
    private var statusIsError by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        recorder = AudioRecorder(this)
        status = getString(R.string.recognize_status_ready)
        statusIsError = false
        setContent {
            HandyTheme {
                val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) startRecording() else finishCanceled()
                }
                val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(stringResource(R.string.recognize_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { innerPadding ->
                    // BoxWithConstraints + heightIn(min = maxHeight) keeps the recorder
                    // vertically centred on short content while still letting the content
                    // scroll (and the large top bar collapse) when it overflows.
                    BoxWithConstraints(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .heightIn(min = maxHeight)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                status,
                                style = MaterialTheme.typography.titleLarge,
                                color = if (statusIsError) MaterialTheme.colorScheme.error else Color.Unspecified,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                            if (recording) CircularProgressIndicator()
                            Button(onClick = {
                                if (recording) stopRecording() else if (hasMicrophonePermission()) startRecording() else requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }) { Text(stringResource(if (recording) R.string.stop else R.string.recognize_record)) }
                        }
                    }
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
            status = getString(R.string.recognize_mic_unavailable)
            statusIsError = true
            return
        }
        recording = true
        AudioFeedbackManager.onStartRecording(this)
        status = getString(R.string.recognize_listening)
        statusIsError = false
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        AudioFeedbackManager.onStopRecording(this)
        status = getString(R.string.transcribing)
        statusIsError = false
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
                status = error.message ?: getString(R.string.recognize_failed)
                statusIsError = true
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
