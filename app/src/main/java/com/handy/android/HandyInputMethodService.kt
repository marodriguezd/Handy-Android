package com.handy.android

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.handy.android.ui.theme.HandyTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HandyInputMethodService : InputMethodService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var recorder: AudioRecorder
    private var transcriptionJob: Job? = null
    private var pushToTalk = false

    // Compose snapshot state bound to the input view; written from the main thread
    // (including the transcription coroutine) and observed by the composition.
    private var statusText by mutableStateOf("")
    private var statusIsError by mutableStateOf(false)
    private var recording by mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        recorder = AudioRecorder(
            context = this,
            onSilenceAutoStop = { mainHandler.post { stopRecording() } },
        )
    }

    override fun onCreateInputView(): View {
        statusText = getString(R.string.ime_ready)
        statusIsError = false
        val view = ComposeView(this)
        // An IME window is not attached to an Activity, so Compose 1.11 would fail to resolve
        // the ViewTree owners ("Composed into the View which doesn't propagate
        // ViewTreeLifecycleOwner!"). Provide lightweight, service-scoped owners on the
        // view itself, as a dialog/overlay would.
        val owner = object : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
            // The IME input view is visible for as long as the window is shown.
            private val lifecycleRegistry = LifecycleRegistry(this).apply {
                currentState = Lifecycle.State.STARTED
            }
            override val lifecycle: Lifecycle get() = lifecycleRegistry
            override val viewModelStore: ViewModelStore = ViewModelStore()
            // Intentionally never attached (no performAttach/performRestore): nothing
            // registers state in the IME window; Compose only needs a non-null owner.
            override val savedStateRegistry: SavedStateRegistry =
                SavedStateRegistryController.create(this).savedStateRegistry
        }
        // Java bridge: the Kotlin compiler cannot resolve the ViewTree* owners from the
        // KMP androidx artifacts, but Compose 1.11 requires them in Activity-less windows.
        ViewTreeOwnerBridge.attach(view, owner, owner, owner)
        view.setContent {
            HandyTheme {
                HandyImeInput(
                    status = statusText,
                    statusIsError = statusIsError,
                    isRecording = recording,
                    onToggle = ::toggleRecording,
                    onHoldStart = { startRecording(pushToTalk = true) },
                    onHoldDragAway = ::stopRecording,
                    holdDurationMs = HOLD_DURATION_MS,
                )
            }
        }
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        statusText = getString(R.string.ime_ready)
        statusIsError = false
    }

    private fun toggleRecording() {
        if (recording) stopRecording() else startRecording()
    }

    private fun startRecording(pushToTalk: Boolean = false) {
        if (recording) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            statusText = getString(R.string.ime_allow_mic)
            statusIsError = true
            return
        }
        if (!recorder.start(enableVoiceActivityDetection = true)) {
            statusText = getString(R.string.recognize_mic_unavailable)
            statusIsError = true
            return
        }
        this.pushToTalk = pushToTalk
        recording = true
        statusIsError = false
        if (pushToTalk) AudioFeedbackManager.onStartPushToTalk(this) else AudioFeedbackManager.onStartRecording(this)
        statusText = getString(if (pushToTalk) R.string.ime_push_to_talk else R.string.recognize_listening)
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        val wasPushToTalk = pushToTalk
        pushToTalk = false
        if (wasPushToTalk) AudioFeedbackManager.onStopPushToTalk(this) else AudioFeedbackManager.onStopRecording(this)
        statusText = getString(R.string.transcribing)
        statusIsError = false
        val samples = recorder.stop()
        val audioPath = runCatching { AudioRecorder.writeWav(this@HandyInputMethodService, samples).absolutePath }.getOrNull()
        transcriptionJob?.cancel()
        transcriptionJob = scope.launch {
            try {
                val text = TranscriptionEngine.transcribe(this@HandyInputMethodService, samples)
                if (text.isNotBlank()) {
                    AudioFeedbackManager.onTranscriptionSuccess(this@HandyInputMethodService)
                    HistoryRepository.record(
                        context = this@HandyInputMethodService,
                        text = text,
                        sourceType = HistorySource.INPUT_METHOD,
                        durationMs = AudioRecorder.durationMs(samples),
                        audioFilePath = audioPath,
                    )
                    currentInputConnection?.commitText(text, 1)
                    if (SettingsManager.autoSubmitEnabled(this@HandyInputMethodService)) {
                        currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
                    }
                }
                statusText = getString(if (text.isBlank()) R.string.ime_no_speech else R.string.ime_ready)
                statusIsError = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                statusText = error.message ?: getString(R.string.recognize_failed)
                statusIsError = true
            }
        }
    }

    override fun onWindowHidden() {
        if (recording) {
            recording = false
            if (pushToTalk) AudioFeedbackManager.onStopPushToTalk(this) else AudioFeedbackManager.onStopRecording(this)
            pushToTalk = false
            recorder.stop()
        }
        super.onWindowHidden()
    }

    override fun onDestroy() {
        transcriptionJob?.cancel()
        scope.cancel()
        recorder.release()
        super.onDestroy()
    }

    companion object {
        private const val HOLD_DURATION_MS = 300L
    }
}

/**
 * Material 3 input view for the Handy voice keyboard.
 *
 * Gesture contract (preserved from the previous Views implementation):
 * - **Tap** (release before the hold duration, without slop) toggles recording via the
 *   button's own [Button] onClick (the pointer handler below never consumes events, so
 *   clicks keep their ripple and the accessibility click action keeps working).
 * - **Hold** past [holdDurationMs] starts push-to-talk recording.
 * - **Release after a hold** stops push-to-talk: the hold already started recording, so
 *   the button's onClick fires a stop toggle.
 * - **Drag away** during a hold cancels the gesture; if push-to-talk was active it is
 *   stopped here (onClick does not fire for a cancelled gesture).
 */
@Composable
private fun HandyImeInput(
    status: String,
    statusIsError: Boolean,
    isRecording: Boolean,
    onToggle: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldDragAway: () -> Unit,
    holdDurationMs: Long,
) {
    // Keep the pointer handler on the latest state/callbacks without restarting the
    // gesture detector on every recomposition.
    val currentIsRecording by rememberUpdatedState(isRecording)
    val currentOnToggle by rememberUpdatedState(onToggle)
    val currentOnHoldStart by rememberUpdatedState(onHoldStart)
    val currentOnHoldDragAway by rememberUpdatedState(onHoldDragAway)

    // The IME is a floating elevated panel, so surfaceContainerLow is the MD3-correct
    // tonal elevation instead of the flat surface role.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    currentIsRecording -> MaterialTheme.colorScheme.primary
                    statusIsError -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { currentOnToggle() },
                colors = if (currentIsRecording) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                modifier = Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // Hold detection via the AwaitPointerEventScope.withTimeoutOrNull
                        // member: the inner wait is cancelled when the hold duration elapses
                        // (→ null → hold fires), while a release/cancel returns a value.
                        // Nothing is consumed, so the Button keeps its ripple and the
                        // accessibility click action, and taps reach onClick normally.
                        val holdFired = withTimeoutOrNull(holdDurationMs) {
                            waitForUpOrCancellation()
                            false
                        } == null
                        if (holdFired && !currentIsRecording) {
                            currentOnHoldStart()
                            val up = waitForUpOrCancellation()
                            // Release after a hold is a valid tap: the Button's own onClick
                            // fires and stops push-to-talk with a single toggle. A cancelled
                            // gesture (drag-away) never fires onClick, so it is stopped here.
                            if (up == null) {
                                currentOnHoldDragAway()
                            }
                        }
                        // Hold while already recording is inert (same as the original).
                    }
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (currentIsRecording) stringResource(R.string.stop) else stringResource(R.string.ime_mic))
            }
        }
    }
}
