package com.handy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.handy.app.bridge.EngineBridge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        System.loadLibrary("handy_core")
        EngineBridge.nativeInit(
            filesDir.resolve("models").absolutePath,
            filesDir.absolutePath,
            object : com.handy.app.bridge.EngineCallback {
                override fun onStateChange(state: Int) {}
                override fun onTranscription(text: String, isPartial: Boolean) {}
                override fun onVadLevel(level: Float) {}
                override fun onError(code: Int, message: String) {}
                override fun onDownloadProgress(modelId: String, bytesSoFar: Long, totalBytes: Long) {}
                override fun onDownloadComplete(modelId: String, success: Boolean, errorMsg: String?) {}
            }
        )

        setContent {
            Text("Handy - Engine init test")
        }
    }
}
