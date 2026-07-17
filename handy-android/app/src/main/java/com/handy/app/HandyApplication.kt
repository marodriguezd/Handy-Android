package com.handy.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.handy.app.audio.FileAudioStorageBackend
import com.handy.app.audio.RecordingRepository
import com.handy.app.bridge.EngineBridge
import com.handy.app.injection.ClipboardInjector
import com.handy.app.injection.InjectorRouter
import com.handy.app.injection.ShizukuInjector
import com.handy.app.util.ReactiveRingBufferLog
import com.handy.app.viewmodel.EngineViewModel
import com.handy.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.sentry.android.core.SentryAndroid
import rikka.shizuku.Shizuku

class HandyApplication : Application(), ComponentCallbacks2 {

    companion object {
        const val QUICK_DICTATE_NOTIFICATION_ID = 1002
        const val CHANNEL_QUICK_DICTATE = "handy_quick_dictate"
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val shizukuInjector: ShizukuInjector by lazy { ShizukuInjector(this) }

    val clipboardInjector: ClipboardInjector by lazy { ClipboardInjector(this) }

    val injectorRouter: InjectorRouter by lazy {
        InjectorRouter(
            shizukuInjector = shizukuInjector,
            clipboardInjector = clipboardInjector,
            settingsStore = settingsStore,
        )
    }

    /**
     * Sprint 25a factory binding: Kotlin-side recording repository that
     * pre-creates a 44-byte WAV header placeholder when the engine
     * starts and finalizes chunk sizes when the engine stops. The flag
     * `SettingsStore.recordingDualWriteMode` is read at construction
     * time so toggling the flag requires an app restart (acceptable —
     * the surface is the same in Sprint 26+ when the persistence flag
     * becomes user-visible in a Compose toggle).
     *
     * TODO(Sprint25b): `pushFloatArrayFrames` is not yet wired to any
     * Kotlin-side capture pipeline. The AAudio callback lives inside
     * the Rust `pipeline.rs` real-time thread, so Kotlin cannot plug
     * into it directly. Sprint 25b will either add a Kotlin
     * frame-subscribe callback (EngineCallback.onAudioFrames + SPSC
     * ring buffer) or move dual-write fully into the Rust pipeline.
     */
    val recordingRepository: RecordingRepository by lazy {
        RecordingRepository(
            storage = FileAudioStorageBackend(this),
            isDualWriteEnabled = settingsStore.recordingDualWriteMode,
            maxStorageBytes = RecordingRepository.DEFAULT_MAX_STORAGE_BYTES,
        )
    }

    /** Singleton engine VM shared by MainActivity and HandyInputMethodService. */
    val engineViewModel: EngineViewModel by lazy {
        EngineViewModel(this, injectorRouter, recordingRepository)
    }

    /**
     * Sprint 28b — process-wide reactive ring buffer for the Debug
     * panel's LiveLogViewer. The buffer itself is JVM-pure (see
     * [com.handy.app.util.RingBufferLog]) so the test suite can run
     * it under plain JUnit; this singleton just instantiates the
     * Compose-reactive wrapper at process scope so any Composable can
     * collect its StateFlow without prop-drilling.
     *
     * Cap of 500 lines matches the historical android.util.Log buffer
     * Handy has been emitting to logcat; raising it would multiply
     * memory pressure without surfacing anything useful for a single
     * developer session.
     */
    val reactiveRingBuffer: ReactiveRingBufferLog by lazy {
        ReactiveRingBufferLog(maxLines = 500)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Run a debug-only test command using the application coroutine scope. */
    fun runTestCommand(block: suspend () -> Unit) {
        appScope.launch(Dispatchers.IO) { block() }
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.SENTRY_DSN.isNotEmpty() && !BuildConfig.SENTRY_DSN.contains("examplePublicKey")) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
            }
        }
        if (!BuildConfig.DEBUG) {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (grantResult == 0) {
                    Log.i("HandyApp", "Shizuku permission granted (code=$requestCode)")
                    shizukuInjector.bindService()
                }
            }
            Shizuku.addBinderReceivedListenerSticky {
                shizukuInjector.bindService()
                if (Shizuku.checkSelfPermission() != 0) {
                    Shizuku.requestPermission(1001)
                }
            }
        }
        engineViewModel
        createQuickDictateChannel()
        showQuickDictateNotification()
    }

    private fun createQuickDictateChannel() {
        // Sprint 23 cleanup: minSdk=26 (Build.VERSION_CODES.O), so the SDK_INT
        // guard is dead code; NotificationChannel APIs are always available.
        val channel = NotificationChannel(
            CHANNEL_QUICK_DICTATE,
            getString(R.string.quick_dictate_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.quick_dictate_channel_description)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun showQuickDictateNotification() {
        val dictateIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("start_dictation", true)
        }
        val dictatePendingIntent = PendingIntent.getActivity(
            this,
            0,
            dictateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val switchKeyboardIntent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val switchKeyboardPendingIntent = PendingIntent.getActivity(
            this,
            1,
            switchKeyboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_QUICK_DICTATE)
            .setContentTitle(getString(R.string.quick_dictate_notification_title))
            .setContentText(getString(R.string.quick_dictate_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(dictatePendingIntent)
            .addAction(
                android.R.drawable.ic_btn_speak_now,
                getString(R.string.quick_dictate_action),
                dictatePendingIntent,
            )
            .addAction(
                android.R.drawable.ic_menu_manage,
                getString(R.string.switch_keyboard),
                switchKeyboardPendingIntent,
            )
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(QUICK_DICTATE_NOTIFICATION_ID, notification)
    }

    @Suppress("DEPRECATION") // ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL is the int-level constant retained for backward compat with minSdk=26; TrimMemoryLevel enum requires API 35+.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            Log.w("HandyApp", "TRIM_MEMORY_RUNNING_CRITICAL - unloading model")
            appScope.launch(Dispatchers.IO) {
                EngineBridge.nativeUnloadModel()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w("HandyApp", "onLowMemory - unloading model")
        appScope.launch(Dispatchers.IO) {
            EngineBridge.nativeUnloadModel()
        }
    }
}
