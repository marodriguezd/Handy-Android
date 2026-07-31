package com.handy.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class LiveSubtitleActivity : ComponentActivity() {
    private var message by mutableStateOf("Live subtitles use a system overlay.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Live subtitles", style = MaterialTheme.typography.headlineSmall)
                    Text(message)
                    Button(onClick = {
                        if (!Settings.canDrawOverlays(this@LiveSubtitleActivity)) {
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                            message = "Enable overlay access, then start again."
                        } else {
                            LiveSubtitleService.start(this@LiveSubtitleActivity)
                            message = "Overlay started"
                        }
                    }) { Text("Start overlay") }
                    Button(onClick = {
                        stopService(Intent(this@LiveSubtitleActivity, LiveSubtitleService::class.java))
                        message = "Overlay stopped"
                    }) { Text("Stop overlay") }
                }
            }
        }
    }
}
