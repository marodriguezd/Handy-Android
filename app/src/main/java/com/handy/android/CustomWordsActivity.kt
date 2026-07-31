package com.handy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class CustomWordsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var words by remember { mutableStateOf(SettingsManager.customWords(this@CustomWordsActivity).joinToString("\n")) }
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Custom words", style = MaterialTheme.typography.headlineSmall)
                    Text("One word or phrase per line.")
                    OutlinedTextField(words, { words = it }, Modifier.weight(1f, fill = true), minLines = 8)
                    Button(onClick = {
                        SettingsManager.setCustomWords(this@CustomWordsActivity, words.lines())
                        finish()
                    }) { Text("Save") }
                }
            }
        }
    }
}
