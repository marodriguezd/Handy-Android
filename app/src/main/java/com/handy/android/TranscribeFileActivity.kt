package com.handy.android

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranscribeFileActivity : ComponentActivity() {
    private var result by mutableStateOf("Choose an audio file to begin")
    private var busy by mutableStateOf(false)
    private var uri by mutableStateOf<Uri?>(null)
    private var uriReadable by mutableStateOf(false)
    private var transcriptionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateIntentUri(intent)
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Transcribe audio", style = MaterialTheme.typography.headlineSmall)
                    Text(result)
                    Button(
                        enabled = uri != null && uriReadable && !busy,
                        onClick = { transcribe() },
                    ) {
                        Text(if (busy) "Transcribing…" else "Transcribe")
                    }
                    Button(
                        enabled = canCopyResult() && !busy,
                        onClick = { copyResult() },
                    ) {
                        Text("Copy result")
                    }
                }
            }
        }
    }

    private fun transcribe() {
        val source = uri ?: return
        if (!uriReadable || busy) return

        transcriptionJob?.cancel()
        busy = true
        result = "Decoding audio…"
        transcriptionJob = lifecycleScope.launch {
            try {
                val samples = withContext(Dispatchers.IO) {
                    AudioFileDecoder.decode(this@TranscribeFileActivity, source)
                }
                if (uri != source) return@launch
                result = "Transcribing…"
                val text = TranscriptionEngine.transcribe(this@TranscribeFileActivity, samples)
                if (uri == source) {
                    result = text.ifBlank { "No speech detected" }
                    if (text.isNotBlank()) {
                        AudioFeedbackManager.onTranscriptionSuccess(this@TranscribeFileActivity)
                        HistoryRepository.record(
                            context = this@TranscribeFileActivity,
                            text = text,
                            sourceType = HistorySource.AUDIO_FILE,
                            durationMs = AudioRecorder.durationMs(samples),
                        )
                        copyResult()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (uri == source) result = error.message ?: "Unable to transcribe audio"
            } finally {
                if (uri == source) busy = false
            }
        }
    }

    private fun copyResult() {
        if (!canCopyResult()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Handy transcription", result))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        transcriptionJob?.cancel()
        setIntent(newIntent)
        updateIntentUri(newIntent)
    }

    override fun onDestroy() {
        transcriptionJob?.cancel()
        super.onDestroy()
    }

    private fun updateIntentUri(sourceIntent: Intent?) {
        val nextUri = intentUri(sourceIntent)
        transcriptionJob?.cancel()
        busy = false
        uri = nextUri
        uriReadable = nextUri?.let(::isReadable) == true
        result = when {
            nextUri == null -> "No audio file was provided"
            !uriReadable -> "The selected audio file cannot be accessed"
            else -> "Choose an audio file to begin"
        }
        val grantFlags = sourceIntent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) ?: 0
        if (nextUri?.scheme == ContentResolverSchemes.CONTENT && grantFlags != 0) {
            runCatching { contentResolver.takePersistableUriPermission(nextUri, grantFlags) }
        }
    }

    private fun isReadable(source: Uri): Boolean = runCatching {
        contentResolver.openFileDescriptor(source, "r")?.use { } ?: return false
        true
    }.getOrDefault(false)

    private fun canCopyResult(): Boolean = result.isNotBlank() && result !in setOf(
        "Choose an audio file to begin",
        "No audio file was provided",
        "The selected audio file cannot be accessed",
        "Decoding audio…",
        "Transcribing…",
        "No speech detected",
    )

    private fun intentUri(sourceIntent: Intent?): Uri? {
        if (sourceIntent == null) return null
        val sharedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sourceIntent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            sourceIntent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val clipUri = sourceIntent.clipData
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.uri
        return when (sourceIntent.action) {
            Intent.ACTION_SEND -> sharedUri ?: clipUri ?: sourceIntent.data
            Intent.ACTION_VIEW -> sourceIntent.data ?: clipUri
            else -> sourceIntent.data ?: sharedUri ?: clipUri
        }
    }

    private object ContentResolverSchemes {
        const val CONTENT = "content"
    }
}
