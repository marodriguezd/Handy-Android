package com.handy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class LiveLogViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LogViewerScreen { finish() } } }
    }
}

@androidx.compose.runtime.Composable
private fun LogViewerScreen(onClose: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var content by remember { mutableStateOf(AppLog.read(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            content = AppLog.read(context)
            delay(750)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Live logs", style = MaterialTheme.typography.headlineSmall)
        Text("Logs stay in the app-private files directory.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(content.ifBlank { "No diagnostic entries yet." }, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { AppLog.clear(context); content = "" }, modifier = Modifier.fillMaxWidth()) { Text("Clear logs") }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    }
}
