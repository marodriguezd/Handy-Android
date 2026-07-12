package com.handy.app.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.HandyApplication
import com.handy.app.R
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
                ImeContent(
                    state = engineViewModel.state.collectAsState().value,
                    partialText = engineViewModel.partialText.collectAsState().value,
                    finalText = engineViewModel.finalText.collectAsState().value,
                    vadLevel = engineViewModel.vadLevel.collectAsState().value,
                    lastErrorMessage = engineViewModel.lastErrorMessage.collectAsState().value,
                    onStartDictation = { startDictation() },
                    onStopDictation = { stopDictation() },
                    onCommitText = { text ->
                        engineViewModel.confirmInsert(text)
                    },
                    onRetry = { engineViewModel.resetPartialText() },
                    onCancelDictation = { engineViewModel.cancelRecording() },
                    onSwitchKeyboard = { switchInputMethod("") }
                )
            }
        }
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        lastInputConnection = currentInputConnection
        // Clear texts without resetting state — engine may be recording mid-dictation
        engineViewModel.clearPartialText()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        lastInputConnection = null
    }

    private fun startDictation() {
        engineViewModel.startRecording()
    }

    private fun stopDictation() {
        engineViewModel.stopRecording()
    }
}

@Composable
private fun ImeContent(
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
    onSwitchKeyboard: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        when (state) {
            0 -> IdleMode(onStartDictation = onStartDictation, onSwitchKeyboard = onSwitchKeyboard)
            1, 2, 3 -> DictatingMode(
                partialText = partialText,
                vadLevel = vadLevel,
                onStopDictation = onStopDictation,
                onCancelDictation = onCancelDictation
            )
            4 -> ErrorMode(
                errorMessage = lastErrorMessage,
                onRetry = onRetry
            )
            5 -> ConfirmMode(
                finalText = finalText ?: partialText,
                onCommitText = onCommitText,
                onRetry = onRetry,
                onSwitchKeyboard = onSwitchKeyboard
            )
            else -> IdleMode(onStartDictation = onStartDictation, onSwitchKeyboard = onSwitchKeyboard)
        }
    }
}

@Composable
private fun IdleMode(
    onStartDictation: () -> Unit,
    onSwitchKeyboard: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = onStartDictation,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "🎤",
                    fontSize = 32.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dictation_button),
                style = MaterialTheme.typography.labelMedium
            )
        }
        IconButton(
            onClick = onSwitchKeyboard,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(text = "⌨", fontSize = 20.sp)
        }
    }
}

@Composable
private fun DictatingMode(
    partialText: String,
    vadLevel: Float,
    onStopDictation: () -> Unit,
    onCancelDictation: () -> Unit
) {
    val animatedLevel by animateFloatAsState(
        targetValue = vadLevel,
        label = "vadLevel"
    )
    val vadColor = when {
        animatedLevel < 0.3f -> Color(0xFF4CAF50)
        animatedLevel < 0.6f -> Color(0xFFFFEB3B)
        else -> Color(0xFFF44336)
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                if (partialText.isNotEmpty()) {
                    Text(
                        text = partialText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = stringResource(R.string.dictation_button),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height((animatedLevel * 48).dp.coerceAtMost(48.dp))
                            .align(Alignment.BottomCenter)
                            .clip(RoundedCornerShape(2.dp))
                            .background(vadColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(animatedLevel * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = onStopDictation,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                ) {
                    Text(text = "■", fontSize = 28.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onCancelDictation) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        }
    }
}

@Composable
private fun ConfirmMode(
    finalText: String,
    onCommitText: (String) -> Unit,
    onRetry: () -> Unit,
    onSwitchKeyboard: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = finalText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { onCommitText(finalText) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(text = stringResource(R.string.insert_text))
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(text = stringResource(R.string.retry))
                }
            }
        }

        IconButton(
            onClick = onSwitchKeyboard,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(text = "⌨", fontSize = 20.sp)
        }
    }
}

@Composable
private fun ErrorMode(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage ?: stringResource(R.string.ime_error_generic),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ime_error_retry_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.retry))
            }
        }
    }
}
