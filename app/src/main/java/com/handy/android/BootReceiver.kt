package com.handy.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED || !SettingsManager.autoStartOnBoot(context)) return
        if (!PermissionChecker.canStartMicrophoneService(context)) {
            AppLog.record(context, "E", "BootReceiver", "RECORD_AUDIO missing, skipping autostart")
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingButtonService::class.java),
            )
        }.onFailure { AppLog.record(context, "E", "BootReceiver", "Unable to start after boot", it) }
    }
}
