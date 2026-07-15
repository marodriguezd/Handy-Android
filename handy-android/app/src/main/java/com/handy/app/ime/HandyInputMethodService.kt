package com.handy.app.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.HandyApplication
import com.handy.app.injection.ImeInjector
import com.handy.app.injection.InjectorRouter
import com.handy.app.viewmodel.EngineViewModel

class HandyInputMethodService : InputMethodService() {

    private val engineViewModel: EngineViewModel
        get() = (application as HandyApplication).engineViewModel

    private val injectorRouter: InjectorRouter
        get() = (application as HandyApplication).injectorRouter

    private val imeInjector = ImeInjector { currentInputConnection ?: lastInputConnection }

    private var lastInputConnection: InputConnection? = null

    override fun onCreate() {
        super.onCreate()
        injectorRouter.setImeInjector(imeInjector)
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).also { view ->
            view.setContent {
                HandyBubble(
                    state = engineViewModel.state.collectAsState().value,
                    partialText = engineViewModel.partialText.collectAsState().value,
                    finalText = engineViewModel.finalText.collectAsState().value,
                    vadLevel = engineViewModel.vadLevel.collectAsState().value,
                    lastErrorMessage = engineViewModel.lastErrorMessage.collectAsState().value,
                    onStartDictation = { engineViewModel.startRecording() },
                    onStopDictation = { engineViewModel.stopRecording() },
                    onCommitText = { text -> engineViewModel.confirmInsert(text) },
                    onRetry = { engineViewModel.resetPartialText() },
                    onCancelDictation = { engineViewModel.cancelRecording() },
                )
            }
        }
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lastInputConnection = currentInputConnection
        engineViewModel.clearPartialText()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lastInputConnection = null
    }

    // Request minimal keyboard height for the bubble
    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        // Don't call super — it sets insets based on the view's measured height,
        // which works but we want to keep the keyboard area as compact as possible.
        // Set contentTopInsets to 0 so the app content isn't pushed up.
        outInsets.contentTopInsets = 0
        outInsets.visibleTopInsets = 0
    }
}

// ── Accent color matching the PC overlay ──────────────────────────
private val AccentPink = Color(0xFFE85D75)
private val SurfaceDark = Color(0xFF1E1E1E)
private val SurfaceLight = Color(0xFFF5F5F5)
private val MutedDark = Color(0xFFA3A09A)
private val FaintDark = Color(0xFF6F6C66)
private val MutedLight = Color(0xFF6E6E6E)
private val FaintLight = Color(0xFF9A9A9A)

@Composable
private fun HandyBubble(
    state: Int,
    partialText: String,
    finalText: String?,
    vadLevel: Float,
    lastErrorMessage: String?,
    onStartDictation: () -> Unit,
    onStopDictation: () -> Unit,
    onCommitText: (String) -> Unit,
    onRetry: () -> Unit,
    onCancelDictation: () -> Unit,
) {
    // State constants
    val STATE_LOADING = 1
    val STATE_LISTENING = 2
    val STATE_TRANSCRIBING = 3
    val STATE_ERROR = 4
    val STATE_CONFIRM = 5

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceDark,
    ) {
        when (state) {
            STATE_CONFIRM -> ConfirmBubble(
                finalText = finalText ?: partialText,
                onCommitText = onCommitText,
                onRetry = onRetry,
            )
            STATE_ERROR -> ErrorBubble(
                errorMessage = lastErrorMessage,
                onRetry = onRetry,
            )
            STATE_LISTENING, STATE_LOADING, STATE_TRANSCRIBING -> RecordingBubble(
                partialText = partialText,
                vadLevel = vadLevel,
                onStop = onStopDictation,
            )
            else -> IdleBubble(onStart = onStartDictation)
        }
    }
}

// ── Idle state: single mic pill ───────────────────────────────────
@Composable
private fun IdleBubble(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "idle")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The pill: mic bubble centered
        Surface(
            modifier = Modifier
                .scale(pulseScale)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onStart() },
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // Pulsing red dot (recording indicator)
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(AccentPink)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "🎙",
                    fontSize = 18.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Dictate",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

// ── Recording state: bubble with waveform + stop ──────────────────
@Composable
private fun RecordingBubble(
    partialText: String,
    vadLevel: Float,
    onStop: () -> Unit,
) {
    val animatedLevel by animateFloatAsState(
        targetValue = vadLevel,
        label = "vadLevel",
        animationSpec = tween(80, easing = LinearEasing),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: pulsing dot
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AccentPink)
                )

                Spacer(Modifier.width(8.dp))

                // Center: waveform bars (9 bars like PC)
                WaveformBars(animatedLevel)

                Spacer(Modifier.width(8.dp))

                // Partial text preview (truncated)
                if (partialText.isNotEmpty()) {
                    Text(
                        text = partialText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                    )
                }

                // Right: stop button (red circle)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onStop() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "■",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

// ── Waveform bars (9 bars like PC overlay) ────────────────────────
@Composable
private fun WaveformBars(level: Float) {
    Row(
        modifier = Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val barCount = 9
        for (i in 0 until barCount) {
            // Create a wave pattern: center bars react more to level
            val centerFactor = 1f - (Math.abs(i - barCount / 2).toFloat() / (barCount / 2f))
            val barHeight = (3 + (level * centerFactor * 14).toInt()).coerceIn(3, 17)

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(AccentPink)
            )
        }
    }
}

// ── Confirm state: show text + insert/retry ───────────────────────
@Composable
private fun ConfirmBubble(
    finalText: String,
    onCommitText: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Text preview
                Text(
                    text = finalText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )

                // Checkmark = insert
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onCommitText(finalText) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    )
                }

                Spacer(Modifier.width(6.dp))

                // Retry
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF757575))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRetry() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↺",
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

// ── Error state: show error + retry ───────────────────────────────
@Composable
private fun ErrorBubble(
    errorMessage: String?,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceDark.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⚠",
                    fontSize = 16.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = errorMessage ?: "Error",
                    color = Color(0xFFE53935),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // Retry button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AccentPink)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onRetry() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↺",
                        color = Color.White,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
