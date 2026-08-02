package com.handy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.android.ui.theme.HandyTheme

class CustomWordsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HandyTheme {
                var words by remember { mutableStateOf(SettingsManager.customWords(this@CustomWordsActivity).joinToString("\n")) }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.custom_words_title)) },
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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.custom_words_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ElevatedCard(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                                OutlinedTextField(
                                    value = words,
                                    onValueChange = { words = it },
                                    modifier = Modifier.fillMaxSize(),
                                    label = { Text(stringResource(R.string.custom_words_field_label)) },
                                )
                            }
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                SettingsManager.setCustomWords(this@CustomWordsActivity, words.lines())
                                finish()
                            },
                        ) { Text(stringResource(R.string.save)) }
                    }
                }
            }
        }
    }
}
