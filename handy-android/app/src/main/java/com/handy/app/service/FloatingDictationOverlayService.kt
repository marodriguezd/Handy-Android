package com.handy.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
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
import com.handy.app.audio.AudioFeedbackPlayer
import com.handy.app.audio.SoundEvent
import com.handy.app.bridge.EngineBridge
import com.handy.app.ui.theme.HandyTheme

/**
 * FloatingDictationOverlayService renders a system-wide floating dictation button/pill
 * over any active app on Android using SYSTEM_ALERT_WINDOW permission.
 *
 * Parallel feature matching the PC desktop version's floating overlay window (overlay.rs).
 *
 * Implements LifecycleOwner + ViewModelStoreOwner + SavedStateRegistryOwner so the embedded
 * ComposeView gets the view-tree owners it requires (ComposeRuntime throws without them
 * for any composable that uses remember / LaunchedEffect / etc.).
 */
class FloatingDictationOverlayService : Service(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore = ViewModelStore()

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var audioFeedbackPlayer: AudioFeedbackPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // SavedState must be attached + restored BEFORE moving the lifecycle to STARTED,
        // so Compose runtime can read restored state during the first composition.
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        // LifecycleRegistry requires the canonical CREATED→STARTED transition; setting
        // STARTED directly from initial state skips the CREATED event that repeatOnLifecycle
        // and LifecycleEventObserver listeners depend on.
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        audioFeedbackPlayer = AudioFeedbackPlayer(this)

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        setupOverlayView()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
        super.onDestroy()
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
    }

    private fun setupOverlayView() {
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 32
            y = 120
        }

        val app = applicationContext as HandyApplication
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingDictationOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingDictationOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingDictationOverlayService)

            setContent {
                val themeModeState = app.settingsStore.themeModeFlow.collectAsState()
                val dynamicColorState = app.settingsStore.dynamicColorFlow.collectAsState()
                HandyTheme(
                    themeModeState = themeModeState,
                    dynamicColorState = dynamicColorState,
                ) {
                    FloatingOverlayContent(
                        audioFeedbackPlayer = audioFeedbackPlayer
                    )
                }
            }
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        composeView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX - (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY - (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(v, layoutParams)
                    true
                }
                else -> false
            }
        }

        overlayView = composeView
        windowManager?.addView(composeView, layoutParams)
    }
}

@Composable
fun FloatingOverlayContent(
    audioFeedbackPlayer: AudioFeedbackPlayer?
) {
    var isRecording by remember { mutableStateOf(false) }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .clickable {
                    if (!isRecording) {
                        try {
                            if (!EngineBridge.nativeIsModelLoaded()) {
                                EngineBridge.nativeLoadModel()
                            }
                            EngineBridge.nativeStartRecording(16000, 1)
                            isRecording = true
                            audioFeedbackPlayer?.playSound(SoundEvent.START)
                        } catch (t: Throwable) {
                            audioFeedbackPlayer?.playSound(SoundEvent.ERROR)
                        }
                    } else {
                        try {
                            EngineBridge.nativeFinalizeStream()
                            isRecording = false
                            audioFeedbackPlayer?.playSound(SoundEvent.STOP)
                        } catch (t: Throwable) {
                            audioFeedbackPlayer?.playSound(SoundEvent.ERROR)
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Dictado Flotante",
                    tint = if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isRecording) "Grabando..." else "Handy",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
