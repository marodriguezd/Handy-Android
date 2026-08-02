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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.handy.android.ui.theme.HandyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
class TranscribeFileActivity : ComponentActivity() {
    private var result by mutableStateOf("")
    private var resultIsError by mutableStateOf(false)
    private var hasResult by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var uri by mutableStateOf<Uri?>(null)
    private var uriReadable by mutableStateOf(false)
    private var transcriptionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateIntentUri(intent)
        setContent {
            HandyTheme {
                val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = { Text(stringResource(R.string.transcribe_file_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { innerPadding ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            result,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (resultIsError) MaterialTheme.colorScheme.error else Color.Unspecified,
                        )
                        Button(
                            enabled = uri != null && uriReadable && !busy,
                            onClick = { transcribe() },
                        ) {
                            Text(stringResource(if (busy) R.string.transcribing else R.string.transcribe_file_transcribe))
                        }
                        Button(
                            enabled = hasResult && !busy,
                            onClick = { copyResult() },
                        ) {
                            Text(stringResource(R.string.transcribe_file_copy))
                        }
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
        hasResult = false
        result = getString(R.string.transcribe_file_decoding)
        resultIsError = false
        transcriptionJob = lifecycleScope.launch {
            try {
                val samples = withContext(Dispatchers.IO) {
                    AudioFileDecoder.decode(this@TranscribeFileActivity, source)
                }
                if (uri != source) return@launch
                result = getString(R.string.transcribing)
                resultIsError = false
                val text = TranscriptionEngine.transcribe(this@TranscribeFileActivity, samples)
                if (uri == source) {
                    if (text.isBlank()) {
                        result = getString(R.string.transcribe_file_no_speech)
                        resultIsError = false
                    } else {
                        result = text
                        hasResult = true
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
                if (uri == source) {
                    result = error.message ?: getString(R.string.transcribe_file_failed)
                    resultIsError = true
                }
            } finally {
                if (uri == source) busy = false
            }
        }
    }

    private fun copyResult() {
        if (!hasResult) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Handy transcription", result))
        Toast.makeText(this, getString(R.string.transcribe_file_copied), Toast.LENGTH_SHORT).show()
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
        hasResult = false
        uri = nextUri
        uriReadable = nextUri?.let(::isReadable) == true
        resultIsError = nextUri != null && !uriReadable
        result = when {
            nextUri == null -> getString(R.string.transcribe_file_none)
            !uriReadable -> getString(R.string.transcribe_file_unreadable)
            else -> getString(R.string.transcribe_file_choose)
        }
        @Suppress("WrongConstant")
        val grantFlags = sourceIntent?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) ?: 0
        if (nextUri?.scheme == ContentResolverSchemes.CONTENT && grantFlags != 0) {
            runCatching { contentResolver.takePersistableUriPermission(nextUri, grantFlags) }
        }
    }

    private fun isReadable(source: Uri): Boolean = runCatching {
        contentResolver.openFileDescriptor(source, "r")?.use { } ?: return false
        true
    }.getOrDefault(false)

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
