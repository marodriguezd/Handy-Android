package com.handy.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Foreground service for microphone recording.
 * Stub — full implementation in Sprint 1 (audio capture + VAD + streaming).
 */
class RecordingService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "RecordingService created (stub)")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "RecordingService onStartCommand: intent=$intent")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "RecordingService destroyed")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "HandyRecording"
    }
}
