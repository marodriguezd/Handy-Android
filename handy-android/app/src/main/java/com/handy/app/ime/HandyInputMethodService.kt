package com.handy.app.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.handy.app.HandyApplication
import com.handy.app.injection.ImeInjector
import com.handy.app.injection.InjectorRouter
import com.handy.app.viewmodel.EngineViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class HandyInputMethodService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val engineViewModel: EngineViewModel
        get() = (application as HandyApplication).engineViewModel

    private val injectorRouter: InjectorRouter
        get() = (application as HandyApplication).injectorRouter

    private val imeInjector = ImeInjector { currentInputConnection ?: lastInputConnection }

    @Volatile
    private var lastInputConnection: InputConnection? = null

    // ── Lifecycle & Owners ────────────────────────────────────
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        injectorRouter.setImeInjector(imeInjector)
        // Enable auto-insert mode: final transcriptions will be automatically injected
        // into the text field via EngineViewModel (bypasses StateFlow conflation issues)
        engineViewModel.setImeModeEnabled(true)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        val dialogWindow = this.window?.window
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            val decorView = dialogWindow.decorView
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            
            val dialogWindow = this@HandyInputMethodService.window?.window
            dialogWindow?.decorView?.let { decorView ->
                decorView.setViewTreeLifecycleOwner(this@HandyInputMethodService)
                decorView.setViewTreeViewModelStoreOwner(this@HandyInputMethodService)
                decorView.setViewTreeSavedStateRegistryOwner(this@HandyInputMethodService)
            }
            
            setViewTreeLifecycleOwner(this@HandyInputMethodService)
            setViewTreeViewModelStoreOwner(this@HandyInputMethodService)
            setViewTreeSavedStateRegistryOwner(this@HandyInputMethodService)
            
            setContent {
                HandyVoiceBar(
                    state = engineViewModel.state.collectAsState().value,
                    vadLevel = engineViewModel.vadLevel.collectAsState().value,
                    lastErrorMessage = engineViewModel.lastErrorMessage.collectAsState().value,
                    onStartDictation = { engineViewModel.startRecording() },
                    onStopDictation = { engineViewModel.stopRecording() },
                    onRetry = {
                        engineViewModel.resetPartialText()
                    },
                    onCancelDictation = { engineViewModel.cancelRecording() },
                    onSwitchKeyboard = { showInputMethodPicker() },
                )
            }
        }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lastInputConnection = currentInputConnection
        engineViewModel.clearPartialText()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lastInputConnection = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    private fun showInputMethodPicker() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            val intent = android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Theme Colors — matching PC overlay design tokens
// ═══════════════════════════════════════════════════════════════════
private val AccentPink = Color(0xFFE85D75)
private val ErrorRed = Color(0xFFE53935)

// ═══════════════════════════════════════════════════════════════════
// HandyVoiceBar — Main composable, handles state machine
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun HandyVoiceBar(
    state: Int,
    vadLevel: Float,
    lastErrorMessage: String?,
    onStartDictation: () -> Unit,
    onStopDictation: () -> Unit,
    onRetry: () -> Unit,
    onCancelDictation: () -> Unit,
    onSwitchKeyboard: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    var visible by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { visible = 1 }

    val scale by animateFloatAsState(
        targetValue = if (visible == 1) 1f else 0.92f,
        animationSpec = tween(460, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)),
        label = "popScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible == 1) 1f else 0f,
        animationSpec = tween(460),
        label = "popAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            shape = RoundedCornerShape(28.dp),
            color = surfaceColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            shadowElevation = 6.dp,
        ) {
        when (state) {
            EngineViewModel.STATE_LISTENING,
            EngineViewModel.STATE_LOADING,
                -> RecordingBar(
                vadLevel = vadLevel,
                onStop = onStopDictation,
            )

            EngineViewModel.STATE_TRANSCRIBING -> TranscribingBar(
                onCancel = onCancelDictation,
            )

            EngineViewModel.STATE_ERROR -> ErrorBar(
                errorMessage = lastErrorMessage,
                onRetry = onRetry,
            )

            else -> IdleBar(
                onStart = onStartDictation,
                onSwitchKeyboard = onSwitchKeyboard,
            )
        }
    }
    }
}

// ═══════════════════════════════════════════════════════════════════
// IdleBar — Mic pill + "Handy" branding + keyboard switch
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun IdleBar(onStart: () -> Unit, onSwitchKeyboard: () -> Unit) {
    val faintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onStart() },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot()
                    Spacer(Modifier.width(10.dp))
                    Text(text = "🎙", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Dictate",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSwitchKeyboard() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "⌨", fontSize = 14.sp, color = faintColor)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// RecordingBar — Waveform + timer + stop button
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun RecordingBar(vadLevel: Float, onStop: () -> Unit) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val faintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot()
                Spacer(Modifier.width(10.dp))
                WaveformBars(level = vadLevel)
                Spacer(Modifier.weight(1f))
                
                val minutes = elapsedSeconds / 60
                val seconds = elapsedSeconds % 60
                Text(
                    text = "%d:%02d".format(minutes, seconds),
                    color = faintColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                )

                Spacer(Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ErrorRed)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onStop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "■", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TranscribingBar — Spinner + "Transcribing…" label + cancel button
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun TranscribingBar(onCancel: () -> Unit) {
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = AccentPink,
                    strokeWidth = 2.dp,
                )

                Spacer(Modifier.width(10.dp))

                Text(
                    text = "Transcribing…",
                    color = mutedColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                )

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ErrorRed)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onCancel() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ErrorBar — Error message + retry button
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun ErrorBar(errorMessage: String?, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "⚠", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = errorMessage ?: "Error",
                    color = ErrorRed,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentPink)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRetry() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "↻", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// PulsingDot — Animated recording indicator (matches PC overlay)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "dotPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((7 * pulseScale).dp)
                .clip(CircleShape)
                .background(AccentPink.copy(alpha = pulseAlpha))
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(AccentPink)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// WaveformBars — 9 reactive bars matching PC overlay design
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun WaveformBars(level: Float) {
    val barCount = 9

    Row(
        modifier = Modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until barCount) {
            val infiniteTransition = rememberInfiniteTransition(label = "bar$i")
            val phase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600 + i * 80, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "phase$i",
            )

            val centerFactor = 1f - (abs(i - barCount / 2).toFloat() / (barCount / 2f))
            val combined = (level * centerFactor * 0.8f + phase * 0.2f).coerceIn(0f, 1f)
            val height = (3 + combined * 15)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentPink)
            )
        }
    }
}
