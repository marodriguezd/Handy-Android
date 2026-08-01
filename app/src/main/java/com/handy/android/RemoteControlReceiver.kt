package com.handy.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class RemoteControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            ACTION_TOGGLE_RECORDING, FloatingButtonService.ACTION_TOGGLE -> FloatingButtonService.ACTION_TOGGLE
            ACTION_CANCEL, FloatingButtonService.ACTION_CANCEL -> FloatingButtonService.ACTION_CANCEL
            else -> return
        }
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingButtonService::class.java).setAction(action),
            )
        }.onFailure { AppLog.record(context, "E", "RemoteControl", "Remote command failed", it) }
    }

    companion object {
        const val ACTION_TOGGLE_RECORDING = "com.handy.android.ACTION_TOGGLE_RECORDING"
        const val ACTION_CANCEL = "com.handy.android.ACTION_CANCEL"
    }
}
