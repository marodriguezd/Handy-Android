package com.handy.android

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.android.ui.theme.HandyTheme

@OptIn(ExperimentalMaterial3Api::class)
class LiveSubtitleActivity : ComponentActivity() {
    private var message by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        message = getString(R.string.live_subtitle_initial)
        setContent {
            HandyTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.live_subtitle_title)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            },
                        )
                    },
                ) { innerPadding ->
                    Column(
                        Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(message, style = MaterialTheme.typography.titleMedium)
                        val micPermissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission(),
                        ) { granted ->
                            if (granted) {
                                LiveSubtitleService.start(this@LiveSubtitleActivity)
                                message = getString(R.string.live_subtitle_started)
                            } else {
                                message = getString(R.string.live_subtitle_perm_mic)
                            }
                        }
                        Button(onClick = {
                            when {
                                !Settings.canDrawOverlays(this@LiveSubtitleActivity) -> {
                                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                                    message = getString(R.string.live_subtitle_enable_overlay)
                                }
                                !PermissionChecker.hasMicrophonePermission(this@LiveSubtitleActivity) ->
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                else -> {
                                    LiveSubtitleService.start(this@LiveSubtitleActivity)
                                    message = getString(R.string.live_subtitle_started)
                                }
                            }
                        }) { Text(stringResource(R.string.live_subtitle_start)) }
                        Button(onClick = {
                            // Lint ImplicitSamInstance false-positive on ::class.java reference.
                            @Suppress("ImplicitSamInstance")
                            stopService(Intent(this@LiveSubtitleActivity, LiveSubtitleService::class.java))
                            message = getString(R.string.live_subtitle_stopped)
                        }) { Text(stringResource(R.string.live_subtitle_stop)) }
                    }
                }
            }
        }
    }
}
