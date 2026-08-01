package com.handy.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PostProcessSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PostProcessSettingsScreen(
                    onOpenCustomWords = {
                        startActivity(Intent(this, CustomWordsActivity::class.java))
                    },
                )
            }
        }
    }
}

@Composable
private fun PostProcessSettingsScreen(onOpenCustomWords: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var processingEnabled by remember { mutableStateOf(SettingsManager.postProcessingEnabled(context)) }
    var capitalizationEnabled by remember { mutableStateOf(SettingsManager.autoCapitalizationEnabled(context)) }
    var punctuationEnabled by remember { mutableStateOf(SettingsManager.punctuationCleanupEnabled(context)) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Post-processing", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Clean up local transcriptions before Handy inserts or saves them. No text leaves the device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingToggle(
            title = "Post-processing",
            description = "Apply vocabulary replacements and enabled text cleanup steps.",
            checked = processingEnabled,
            onCheckedChange = {
                processingEnabled = it
                SettingsManager.setPostProcessingEnabled(context, it)
            },
        )
        SettingToggle(
            title = "Auto-capitalize",
            description = "Capitalize sentence starts and the isolated pronoun “i”.",
            checked = capitalizationEnabled,
            enabled = processingEnabled,
            onCheckedChange = {
                capitalizationEnabled = it
                SettingsManager.setAutoCapitalizationEnabled(context, it)
            },
        )
        SettingToggle(
            title = "Punctuation cleanup",
            description = "Remove spaces before punctuation and collapse duplicate spaces.",
            checked = punctuationEnabled,
            enabled = processingEnabled,
            onCheckedChange = {
                punctuationEnabled = it
                SettingsManager.setPunctuationCleanupEnabled(context, it)
            },
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenCustomWords,
        ) {
            Text("Edit custom words and rules")
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
