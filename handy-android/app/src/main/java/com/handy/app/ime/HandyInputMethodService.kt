package com.handy.app.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
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
import com.handy.app.R
import com.handy.app.injection.ImeInjector
import com.handy.app.injection.InjectorRouter
import com.handy.app.viewmodel.EngineViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════
// HandyInputMethodService — IME lifecycle + Compose host
// ═══════════════════════════════════════════════════════════════════
class HandyInputMethodService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val engineViewModel: EngineViewModel
        get() = (application as HandyApplication).engineViewModel

    private val injectorRouter: InjectorRouter
        get() = (application as HandyApplication).injectorRouter

    private val imeInjector = ImeInjector { currentInputConnection ?: lastInputConnection }

    @Volatile
    private var lastInputConnection: InputConnection? = null

    /**
     * Height in pixels of the visible IME content panel.
     * Measured dynamically via onGloballyPositioned in Compose
     * and consumed by onComputeInsets to prevent host app layout shifts.
     */
    @Volatile
    private var contentHeightPx: Int = 0

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
                val finalText by engineViewModel.finalText.collectAsState()
                Box(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        contentHeightPx = coordinates.size.height
                    }
                ) {
                    HandyVoiceBar(
                        state = engineViewModel.state.collectAsState().value,
                        vadLevel = engineViewModel.vadLevel.collectAsState().value,
                        partialText = engineViewModel.partialText.collectAsState().value,
                        finalText = finalText,
                        lastErrorMessage = engineViewModel.lastErrorMessage.collectAsState().value,
                        onStartDictation = { engineViewModel.startRecording() },
                        onStopDictation = { engineViewModel.stopRecording() },
                        onConfirmInsert = { text -> engineViewModel.confirmInsert(text) },
                        onDiscard = { engineViewModel.resetPartialText() },
                        onRetry = { engineViewModel.resetPartialText() },
                        onCancelDictation = { engineViewModel.cancelRecording() },
                        onSwitchKeyboard = { showInputMethodPicker() },
                    )
                }
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

    /**
     * Control the visible area of the IME to avoid unexpected layout shifts
     * in host apps. Since Handy uses a floating pill design (not a full-height
     * keyboard), we tell the system that only the pill's measured height is
     * "content" and the rest of the IME window is transparent background.
     *
     * Without this override, Android assumes the entire IME window height is
     * content and pushes the host app up into the top of the screen, causing
     * jarring layout jumps.
     */
    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (outInsets == null) return

        val height = contentHeightPx
        if (height > 0) {
            outInsets.contentTopInsets = height
            outInsets.visibleTopInsets = height
            // Only the pill area is touchable; taps in the transparent
            // background area pass through to the host app below.
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_CONTENT
        }
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
// Design Tokens
// ═══════════════════════════════════════════════════════════════════
private val AccentPink = Color(0xFFE85D75)
private val AccentPinkDark = Color(0xFFD04A60)
private val GreenSuccess = Color(0xFF4CAF50)
private val GreenSuccessDark = Color(0xFF388E3C)
private val ErrorRed = Color(0xFFE53935)
private val TranscribingPurple = Color(0xFF9C27B0)

/** Easing for pop/spring-like animations — overshoot then settle */
private val PopEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Easing for smooth entrances — fast start, slow end */
private val EnterEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

// ═══════════════════════════════════════════════════════════════════
// Helper: animated press scale modifier
// ═══════════════════════════════════════════════════════════════════

/**
 * Wraps a clickable lambda with press-scale animation.
 * Returns a [ClickableWithPress] that must be used with [pressClickable].
 */
private class ClickableWithPress(
    val interactionSource: MutableInteractionSource,
    val onClick: () -> Unit,
)

@Composable
private fun rememberPressScaleClickable(onClick: () -> Unit): ClickableWithPress {
    val interactionSource = remember { MutableInteractionSource() }
    return ClickableWithPress(interactionSource, onClick)
}

@Composable
private fun Modifier.pressScaleClickable(
    clickable: ClickableWithPress,
    scalePressed: Float = 0.92f,
): Modifier {
    val isPressed by clickable.interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scalePressed else 1f,
        animationSpec = tween(100),
        label = "pressScale",
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = clickable.interactionSource,
            indication = null,
        ) { clickable.onClick() }
}

// ═══════════════════════════════════════════════════════════════════
// HandyVoiceBar — Main composable with animated state transitions
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun HandyVoiceBar(
    state: Int,
    vadLevel: Float,
    partialText: String,
    finalText: String?,
    lastErrorMessage: String?,
    onStartDictation: () -> Unit,
    onStopDictation: () -> Unit,
    onConfirmInsert: (String) -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
    onCancelDictation: () -> Unit,
    onSwitchKeyboard: () -> Unit,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)

    // Pop-in animation on first render
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val popScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(500, easing = PopEasing),
        label = "popScale",
    )
    val popAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(400),
        label = "popAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = popScale
                    scaleY = popScale
                    this.alpha = popAlpha
                },
            shape = RoundedCornerShape(28.dp),
            color = surfaceColor,
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            shadowElevation = 8.dp,
        ) {
            // AnimatedContent handles smooth transitions between states.
            // Each state slides in from above/below with a fade, creating
            // a smooth visual flow as the user progresses through the
            // dictation lifecycle.
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    ContentTransform(
                        targetContentEnter = slideInVertically(
                            animationSpec = tween(300, easing = EnterEasing),
                            // Slide from 1/4 of the container height above/below
                            initialOffsetY = { fullHeight -> fullHeight / 4 * -direction },
                        ) + fadeIn(animationSpec = tween(250)),
                        initialContentExit = fadeOut(animationSpec = tween(150)),
                    )
                },
                label = "stateTransition",
            ) { currentState ->
                when (currentState) {
                    EngineViewModel.STATE_LOADING -> LoadingBar()

                    EngineViewModel.STATE_LISTENING -> RecordingBar(
                        vadLevel = vadLevel,
                        partialText = partialText,
                        onStop = onStopDictation,
                    )

                    EngineViewModel.STATE_TRANSCRIBING -> TranscribingBar(
                        partialText = partialText,
                        onCancel = onCancelDictation,
                    )

                    EngineViewModel.STATE_CONFIRM -> ConfirmBar(
                        text = finalText ?: partialText,
                        onConfirm = onConfirmInsert,
                        onDiscard = onDiscard,
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
}

// ═══════════════════════════════════════════════════════════════════
// IdleBar — Mic pill + "Dictate" label + keyboard switcher
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun IdleBar(onStart: () -> Unit, onSwitchKeyboard: () -> Unit) {
    val faintColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    val startClick = rememberPressScaleClickable(onStart)
    val kbClick = rememberPressScaleClickable(onSwitchKeyboard)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .pressScaleClickable(startClick, scalePressed = 0.97f),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    // Subtle idle pulsing dot
                    IdlePulsingDot()
                    Spacer(Modifier.width(10.dp))
                    // Microphone icon in subtle pink circle
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(AccentPink.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "\uD83C\uDF99", fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.dictation_button),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Keyboard switcher button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .pressScaleClickable(kbClick, scalePressed = 0.88f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "\u2328", fontSize = 15.sp, color = faintColor)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// LoadingBar — Model loading spinner
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun LoadingBar() {
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

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
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AccentPink,
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.ime_loading_model),
                    color = mutedColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// RecordingBar — Waveform + timer + partial text + stop button
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun RecordingBar(vadLevel: Float, partialText: String, onStop: () -> Unit) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsedSeconds++
        }
    }

    val stopClick = rememberPressScaleClickable(onStop)

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val timerText = "%d:%02d".format(minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top row: dot, waveform, timer, stop
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

                    // Timer badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = timerText,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // Stop button with red glow shadow
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .shadow(
                                elevation = 6.dp,
                                shape = CircleShape,
                                ambientColor = ErrorRed.copy(alpha = 0.3f),
                                spotColor = ErrorRed.copy(alpha = 0.3f),
                            )
                            .clip(CircleShape)
                            .background(ErrorRed)
                            .pressScaleClickable(stopClick, scalePressed = 0.85f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\u25A0",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Partial text preview in a separated surface
                if (partialText.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    ) {
                        Text(
                            text = partialText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TranscribingBar — Spinner + partial text + cancel button
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun TranscribingBar(partialText: String, onCancel: () -> Unit) {
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    val cancelClick = rememberPressScaleClickable(onCancel)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Animated purple spinner
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = TranscribingPurple,
                        strokeWidth = 2.5.dp,
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = stringResource(R.string.ime_transcribing),
                        color = mutedColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    Spacer(Modifier.weight(1f))

                    // Cancel button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(ErrorRed)
                            .pressScaleClickable(cancelClick, scalePressed = 0.85f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "\u2715",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (partialText.isNotEmpty()) {
                    Text(
                        text = partialText,
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ConfirmBar — Shows transcribed text with Insert/Discard options
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun ConfirmBar(
    text: String,
    onConfirm: (String) -> Unit,
    onDiscard: () -> Unit,
) {
    val mutedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val context = LocalContext.current

    val insertClick = rememberPressScaleClickable { onConfirm(text) }
    val discardClick = rememberPressScaleClickable(onDiscard)
    val copyClick = rememberPressScaleClickable {
        val clipboard = context.getSystemService(
            android.content.Context.CLIPBOARD_SERVICE
        ) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(
            android.content.ClipData.newPlainText(
                context.getString(R.string.app_name), text
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Transcribed text display with copy button
                if (text.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(Modifier.width(8.dp))

                        // Copy to clipboard button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(mutedColor.copy(alpha = 0.08f))
                                .pressScaleClickable(copyClick, scalePressed = 0.88f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "\uD83D\uDCCB", fontSize = 13.sp)
                        }
                    }
                }

                // Action buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left: hint text
                    Text(
                        text = stringResource(R.string.ime_tap_insert_to_use),
                        color = mutedColor.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(start = 6.dp),
                    )

                    // Right: action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Discard button (outlined style)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(mutedColor.copy(alpha = 0.12f))
                                .pressScaleClickable(discardClick, scalePressed = 0.92f)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.discard),
                                color = mutedColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        // Insert button (filled green with shadow)
                        Box(
                            modifier = Modifier
                                .shadow(
                                    elevation = 4.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = GreenSuccess.copy(alpha = 0.3f),
                                    spotColor = GreenSuccess.copy(alpha = 0.3f),
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(GreenSuccess)
                                .pressScaleClickable(insertClick, scalePressed = 0.92f)
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "\u2713",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(R.string.insert_text),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
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
    val retryClick = rememberPressScaleClickable(onRetry)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = ErrorRed.copy(alpha = 0.08f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                ErrorRed.copy(alpha = 0.2f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Error icon in subtle red circle
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ErrorRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "!",
                        color = ErrorRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.width(10.dp))

                Text(
                    text = errorMessage ?: stringResource(R.string.ime_error_generic),
                    color = ErrorRed,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(10.dp))

                // Retry button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AccentPink)
                        .pressScaleClickable(retryClick, scalePressed = 0.88f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "\u21BB",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// IdlePulsingDot — Subtle idle indicator (no recording happening)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun IdlePulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "idleDot")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idlePulseAlpha",
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idlePulseScale",
    )

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((6 * pulseScale).dp)
                .clip(CircleShape)
                .background(AccentPink.copy(alpha = pulseAlpha))
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(AccentPink.copy(alpha = 0.5f))
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// PulsingDot — Animated recording indicator (active)
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
        modifier = Modifier.height(20.dp),
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
            val barHeight = (3 + combined * 16)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentPink)
            )
        }
    }
}
