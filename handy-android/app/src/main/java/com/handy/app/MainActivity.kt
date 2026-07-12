package com.handy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.handy.app.viewmodel.EngineViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm = remember { (application as HandyApplication).engineViewModel }
            val app = remember { (application as HandyApplication) }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Handy - Power User Test",
                        style = MaterialTheme.typography.headlineMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = {
                        vm.testInject("Hello! This is a test transcription from Handy.")
                    }) {
                        Text("Test Text Injection")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(onClick = {
                        app.shizukuInjector.requestPermissionIfNeeded(this@MainActivity)
                    }) {
                        Text("Grant Shizuku Permission")
                    }
                }
            }
        }
    }
}
