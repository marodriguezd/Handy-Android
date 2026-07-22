package com.handy.app.ui.recognize

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handy.app.HandyApplication
import com.handy.app.R
import com.handy.app.bridge.EngineBridge
import com.handy.app.corrector.WordCorrector
import com.handy.app.ui.theme.HandyTheme

class RecognizeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        setContent {
            val app = (applicationContext as HandyApplication)
            val themeModeState = app.settingsStore.themeModeFlow.collectAsState()
            val dynamicColorState = app.settingsStore.dynamicColorFlow.collectAsState()
            HandyTheme(
                themeModeState = themeModeState,
                dynamicColorState = dynamicColorState,
            ) {
                RecognizeSheetContent(
                    onStopAndFinish = { text ->
                        if (text.isNotBlank()) {
                            val results = arrayListOf(text)
                            val data = Intent().apply {
                                putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results)
                            }
                            setResult(Activity.RESULT_OK, data)
                        } else {
                            setResult(Activity.RESULT_CANCELED)
                        }
                        finish()
                    },
                    onCancel = {
                        try {
                            if (EngineBridge.nativeIsRecording()) {
                                EngineBridge.nativeCancelRecording()
                            }
                        } catch (ignored: Throwable) {}
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }

        try {
            if (!EngineBridge.nativeIsRecording()) {
                if (!EngineBridge.nativeIsModelLoaded()) {
                    EngineBridge.nativeLoadModel()
                }
                EngineBridge.nativeStartRecording(16000, 1)
            }
        } catch (t: Throwable) {
            android.util.Log.e("RecognizeActivity", "Error starting recording", t)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (EngineBridge.nativeIsRecording()) {
                EngineBridge.nativeCancelRecording()
            }
        } catch (ignored: Throwable) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizeSheetContent(
    onStopAndFinish: (String) -> Unit,
    onCancel: () -> Unit
) {
    var isRecording by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Escuchando...") }
    var partialText by remember { mutableStateOf("") }
    var vadLevel by remember { mutableFloatStateOf(0.2f) }

    val animatedScale by animateFloatAsState(
        targetValue = 1f + (vadLevel * 0.4f),
        label = "vadPulse"
    )

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Handy Voice Input",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(96.dp * animatedScale)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable {
                        if (isRecording) {
                            isRecording = false
                            statusText = "Procesando..."
                            try {
                                EngineBridge.nativeFinalizeStream()
                            } catch (t: Throwable) {
                                onStopAndFinish(partialText)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Mic else Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (partialText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = partialText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (isRecording) {
                        isRecording = false
                        statusText = "Finalizando..."
                        try {
                            EngineBridge.nativeFinalizeStream()
                        } catch (t: Throwable) {
                            onStopAndFinish(partialText)
                        }
                    } else {
                        onStopAndFinish(partialText)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRecording) "Detener y Enviar" else "Listo")
            }
        }
    }
}
