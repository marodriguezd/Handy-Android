package com.handy.app.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.handy.app.ui.components.HandySpringTokens
import com.handy.app.ui.components.Spacing
import com.handy.app.viewmodel.EngineViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

// ═══════════════════════════════════════════════════════════════════
// HandyInputMethodService — IME lifecycle + Compose host
// ═══════════════════════════════════════════════════════════════════
class HandyInputMethodService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val engineViewModel: EngineViewModel
        get() = (application as HandyApplication).engineViewModel

    private val injectorRouter: InjectorRouter
        get() = (application as HandyApplication).injectorRouter

    private val imeInjector = ImeInjector { currentInputConnection ?: lastInputConnection }

    @Volatile
    private var lastInputConnection: InputConnection? = null

    @Volatile
    var lastTargetPackage: String? = null
        private set

    /**
     * Height in pixels of the visible IME content panel, measured
     * dynamically via `onGloballyPositioned` and consumed by
     * `onComputeInsets` so the host app isn't pushed upward into a
     * fullscreen IME chrome (we use a floating pill, not a full keyboard).
     */
    @Volatile
    private var contentHeightPx: Int = 0

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        injectorRouter.setImeInjector(imeInjector)
        engineViewModel.setImeModeEnabled(true)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val dialogWindow = this.window?.window
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),
            )
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
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
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
                // Sprint 21: subscribe to user-forced pill placement so the
                // IME re-aligns (top vs bottom center) without restarting.
                val imePlacement by (application as HandyApplication)
                    .settingsStore.imePlacementFlow.collectAsState()
                val finalText by engineViewModel.finalText.collectAsState()

                Box(
                    modifier = Modifier.onGloballyPositioned { coords ->
                        contentHeightPx = coords.size.height
                    },
                ) {
                    HandyVoiceBar(
                        state = engineViewModel.state.collectAsState().value,
                        vadLevel = engineViewModel.vadLevel.collectAsState().value,
                        partialText = engineViewModel.partialText.collectAsState().value,
                        finalText = finalText,
                        lastErrorMessage = engineViewModel.lastErrorMessage.collectAsState().value,
                        imePlacement = imePlacement,
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
        lastTargetPackage = attribute?.packageName
        engineViewModel.clearPartialText()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lastInputConnection = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    /**
     * Floating pill = wrap_content height, transparent below. Without
     * this override Android assumes the entire IME window is content
     * and pushes the host app up. We declare only the pill height as
     * `contentTopInsets`, leave the pass-through area above
     * touchable via `TOUCHABLE_INSETS_REGION` so taps elsewhere fall
     * through to the host app underneath.
     */
    override fun onComputeInsets(outInsets: Insets?) {
        super.onComputeInsets(outInsets)
        if (outInsets == null) return

        val height = contentHeightPx
        if (height > 0) {
            outInsets.contentTopInsets = height
            outInsets.visibleTopInsets = height
            outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            val width = window?.window?.decorView?.width
                .takeIf { it != null && it > 0 }
                ?: resources.displayMetrics.widthPixels
            outInsets.touchableRegion.set(0, 0, width, height)
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    private fun showInputMethodPicker() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
            imm.showInputMethodPicker()
        } catch (e: Exception) {
            val intent = android.content.Intent(
                android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS,
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// Design Tokens
// ═══════════════════════════════════════════════════════════════════
private val EnterEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private const val FinitePhaseDurationMs = 1900L

// MD3 "full pill" approximation — Compose Material3 only ships
// extraSmall..extraLarge, so the spec-mandated full-rounded pill is
// rendered as RoundedCornerShape(28.dp) instead of MaterialTheme.
private val PillShape = RoundedCornerShape(28.dp)

// ═══════════════════════════════════════════════════════════════════
// Helper: spring-driven press-scale interaction
// ═══════════════════════════════════════════════════════════════════
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
        animationSpec = HandySpringTokens.gentle(),
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
// HandyVoiceBar — Compose state machine
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun HandyVoiceBar(
    state: Int,
    vadLevel: Float,
    partialText: String,
    finalText: String?,
    lastErrorMessage: String?,
    imePlacement: String,
    onStartDictation: () -> Unit,
    onStopDictation: () -> Unit,
    onConfirmInsert: (String) -> Unit,
    onDiscard: () -> Unit,
    onRetry: () -> Unit,
    onCancelDictation: () -> Unit,
    onSwitchKeyboard: () -> Unit,
) {
    val isTop = imePlacement == "top"

    // Pop-in on first composition
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val popScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = HandySpringTokens.bouncy(),
        label = "popScale",
    )
    val popAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = HandySpringTokens.gentle(),
        label = "popAlpha",
    )

    Box(
        // Reviewer note: `fillMaxWidth()` was originally `fillMaxWidth()`
        // (wrap-content height).  `fillMaxSize()` here would carry the
        // entire IME window into `onGloballyPositioned`, collapsing the
        // pass-through area to zero in `onComputeInsets`.
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = if (isTop) Spacing.huge else Spacing.sm,
                bottom = if (isTop) Spacing.sm else Spacing.huge,
            ),
        contentAlignment = if (isTop) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        AnimatedVisibility(visible = visible) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = popScale
                        scaleY = popScale
                        this.alpha = popAlpha
                    },
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                border = errorBorderFor(state),
            ) {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        ContentTransform(
                            targetContentEnter = slideInVertically(
                                animationSpec = tween(300, easing = EnterEasing),
                                initialOffsetY = { fullHeight -> fullHeight / 4 * -direction },
                            ) + fadeIn(animationSpec = tween(250)),
                            initialContentExit = fadeOut(animationSpec = tween(150)),
                        )
                    },
                    label = "voiceBarState",
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
}

/** Spec: error surface gets a subtle border.  @Composable because we
 *  read `MaterialTheme.colorScheme.error` to build the tinted border. */
@Composable
private fun errorBorderFor(state: Int): BorderStroke? = when (state) {
    EngineViewModel.STATE_ERROR -> BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
    )
    else -> null
}

// ═══════════════════════════════════════════════════════════════════
// IdleBar — pill + idle pulsing dot + label + keyboard switcher
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun IdleBar(onStart: () -> Unit, onSwitchKeyboard: () -> Unit) {
    val startClick = rememberPressScaleClickable(onStart)
    val kbClick = rememberPressScaleClickable(onSwitchKeyboard)

    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleClickable(startClick, scalePressed = 0.97f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xxl, end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdlePulsingDot()
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = stringResource(R.string.dictation_button),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.weight(1f))

            FilledIconButton(
                onClick = { kbClick.onClick() },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = stringResource(R.string.switch_keyboard),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// LoadingBar — model load spinner
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun LoadingBar() {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xxl, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = stringResource(R.string.ime_loading_model),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// RecordingBar — waveform + timer + partial text + stop
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
    val timerText = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)

    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PulsingDot()
                Spacer(Modifier.width(Spacing.md))
                WaveformBars(level = vadLevel)
                Spacer(Modifier.weight(1f))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = timerText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            horizontal = Spacing.sm,
                            vertical = 2.dp,
                        ),
                    )
                }

                Spacer(Modifier.width(Spacing.sm))

                FilledIconButton(
                    onClick = { stopClick.onClick() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.stop_dictation),
                    )
                }
            }

            if (partialText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.sm,
                    ),
                ) {
                    Text(
                        text = partialText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            horizontal = Spacing.md,
                            vertical = Spacing.sm,
                        ),
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// TranscribingBar — spinner + partial text + cancel button
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun TranscribingBar(partialText: String, onCancel: () -> Unit) {
    val cancelClick = rememberPressScaleClickable(onCancel)

    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = stringResource(R.string.ime_transcribing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))

                FilledIconButton(
                    onClick = { cancelClick.onClick() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.discard),
                    )
                }
            }
            if (partialText.isNotEmpty()) {
                Text(
                    text = partialText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = Spacing.sm,
                    ),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ConfirmBar — transcribed text + Insert/Discard (with HorizontalDivider)
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun ConfirmBar(
    text: String,
    onConfirm: (String) -> Unit,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    val insertClick = rememberPressScaleClickable { onConfirm(text) }
    val discardClick = rememberPressScaleClickable(onDiscard)
    val copyClick = rememberPressScaleClickable {
        val clipboard = context.getSystemService(
            android.content.Context.CLIPBOARD_SERVICE,
        ) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(
            android.content.ClipData.newPlainText(
                context.getString(R.string.app_name),
                text,
            ),
        )
    }

    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Transcribed text + always-visible copy button.
            if (text.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = Spacing.lg,
                            top = Spacing.md,
                            end = Spacing.sm,
                            bottom = Spacing.xs,
                        ),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        // Spec: 4 lines max on confirm bar
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    FilledIconButton(
                        onClick = { copyClick.onClick() },
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.text_copied_to_clipboard),
                        )
                    }
                }
            }

            // Spec: HorizontalDivider before actionables.
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )

            // Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ime_tap_insert_to_use),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(
                        onClick = { discardClick.onClick() },
                    ) {
                        Text(stringResource(R.string.discard))
                    }
                    FilledTonalButton(
                        onClick = { insertClick.onClick() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(stringResource(R.string.insert_text))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// ErrorBar — error surface + retry
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun ErrorBar(errorMessage: String?, onRetry: () -> Unit) {
    val retryClick = rememberPressScaleClickable(onRetry)

    Surface(
        shape = PillShape,
        // Spec: error surface = errorContainer.copy(alpha = 0.08f)
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f),
        tonalElevation = 3.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = errorMessage ?: stringResource(R.string.ime_error_generic),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            FilledIconButton(
                onClick = { retryClick.onClick() },
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.retry),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// IdlePulsingDot — slow breathing dot before any recording begins
// ═══════════════════════════════════════════════════════════════════
//
// We approximate "infinite spring" by toggling a phase Boolean under
// a `LaunchedEffect` and binding two `animateFloatAsState`s with spring
// specs. Compose's `infiniteRepeatable` only accepts DurationBased
// animation specs and rejects `spring`, so this state-toggling pattern
// gives us spring physics with infinite cycling.
@Composable
private fun IdlePulsingDot() {
    var phase by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(FinitePhaseDurationMs + 600)  // slightly slower than the recording pulse
            phase = !phase
        }
    }
    val pulseAlpha by animateFloatAsState(
        targetValue = if (phase) 0.1f else 0.35f,
        animationSpec = HandySpringTokens.gentle(),
        label = "idlePulseAlpha",
    )
    val pulseScale by animateFloatAsState(
        targetValue = if (phase) 1.4f else 1f,
        animationSpec = HandySpringTokens.bouncy(),
        label = "idlePulseScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
        Box(
            modifier = Modifier
                .size((6 * pulseScale).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// PulsingDot — active recording dot
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun PulsingDot() {
    var phase by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(FinitePhaseDurationMs)
            phase = !phase
        }
    }
    val pulseAlpha by animateFloatAsState(
        targetValue = if (phase) 0f else 0.6f,
        animationSpec = HandySpringTokens.gentle(),
        label = "pulseAlpha",
    )
    val pulseScale by animateFloatAsState(
        targetValue = if (phase) 1.8f else 1f,
        animationSpec = HandySpringTokens.bouncy(),
        label = "pulseScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
        Box(
            // Reviewer note: keep the recording dot on the brand color
            // (primary = Handy pink) — using `error` (red) reads as
            // "stop / fail". The IdlePulsingDot above uses primary too;
            // keep these consistent.
            modifier = Modifier
                .size((8 * pulseScale).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)),
        )
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
// WaveformBars — 9 spring-driven bars matching the PC overlay
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun WaveformBars(level: Float) {
    val barCount = 9
    Row(
        modifier = Modifier.height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until barCount) {
            var phase by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay((600 + i * 80).toLong())
                    phase = !phase
                }
            }
            val phaseAxis by animateFloatAsState(
                targetValue = if (phase) 1f else 0.15f,
                animationSpec = HandySpringTokens.bouncy(),
                label = "phase$i",
            )
            val centerFactor = 1f - (abs(i - barCount / 2).toFloat() / (barCount / 2f))
            val combined = (level * centerFactor * 0.8f + phaseAxis * 0.2f).coerceIn(0f, 1f)
            val barHeight = (3 + combined * 16)

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// TODO(Sprint22): introduce a confirming-cursor (1s blink) before
//  transitioning out of STATE_CONFIRM.
