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
import com.handy.app.bridge.EngineBridge
import com.handy.app.injection.ClipboardInjector
import com.handy.app.injection.InjectorRouter
import com.handy.app.injection.ShizukuInjector
import com.handy.app.viewmodel.EngineViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import io.sentry.android.core.SentryAndroid
import moe.shizuku.api.Shizuku

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

    /** Singleton engine VM shared by MainActivity and HandyInputMethodService. */
    val engineViewModel: EngineViewModel by lazy {
        EngineViewModel(this, injectorRouter)
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        SentryAndroid.init(this) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
        }
        Shizuku.initialize(this)
        Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
            if (grantResult == Shizuku.PERMISSION_GRANTED) {
                Log.i("HandyApp", "Shizuku permission granted (code=$requestCode)")
                shizukuInjector.bindService()
            }
        }
        shizukuInjector.bindService()
        engineViewModel
        createQuickDictateChannel()
        showQuickDictateNotification()
    }

    private fun createQuickDictateChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            Log.w("HandyApp", "TRIM_MEMORY_RUNNING_CRITICAL - unloading model")
            appScope.launch(Dispatchers.IO) {
                EngineBridge.nativeUnloadModel()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}

    override fun onLowMemory() {
        Log.w("HandyApp", "onLowMemory - unloading model")
        appScope.launch(Dispatchers.IO) {
            EngineBridge.nativeUnloadModel()
        }
    }
}
