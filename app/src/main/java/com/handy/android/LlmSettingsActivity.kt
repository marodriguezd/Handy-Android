package com.handy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class LlmSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LlmSettingsScreen { finish() } } }
    }
}

@Composable
private fun LlmSettingsScreen(onSaved: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsManager.llmEnabled(context)) }
    var endpoint by remember { mutableStateOf(SettingsManager.llmEndpoint(context)) }
    var apiKey by remember { mutableStateOf(SettingsManager.llmApiKey(context)) }
    var model by remember { mutableStateOf(SettingsManager.llmModel(context)) }
    var prompt by remember { mutableStateOf(SettingsManager.llmSystemPrompt(context)) }
    var templateName by remember { mutableStateOf("") }
    var templates by remember { mutableStateOf(SettingsManager.llmPromptTemplates(context)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.llm_settings_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.llm_settings_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitch(
            title = stringResource(R.string.llm_enable_label),
            checked = enabled,
            onCheckedChange = { enabled = it },
        )
        OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.llm_endpoint_label)) }, singleLine = true)
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.llm_api_key_label)) }, singleLine = true)
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.llm_model_label)) }, singleLine = true)
        OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.llm_system_prompt_label)) }, minLines = 4)

        Text("Prompt library", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            templateName,
            { templateName = it },
            Modifier.fillMaxWidth(),
            label = { Text("Template name") },
            singleLine = true,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = templateName.isNotBlank() && prompt.isNotBlank(),
                onClick = {
                    SettingsManager.saveLlmPromptTemplate(context, SettingsManager.PromptTemplate(templateName, prompt))
                    templates = SettingsManager.llmPromptTemplates(context)
                    templateName = ""
                },
            ) { Text("Save template") }
            OutlinedButton(
                enabled = templateName.isNotBlank(),
                onClick = {
                    SettingsManager.deleteLlmPromptTemplate(context, templateName)
                    templates = SettingsManager.llmPromptTemplates(context)
                    templateName = ""
                },
            ) { Text("Delete") }
        }
        templates.forEach { template ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    templateName = template.name
                    prompt = template.prompt
                },
            ) { Text(template.name) }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                SettingsManager.setLlmEnabled(context, enabled)
                SettingsManager.setLlmEndpoint(context, endpoint)
                SettingsManager.setLlmApiKey(context, apiKey)
                SettingsManager.setLlmModel(context, model)
                SettingsManager.setLlmSystemPrompt(context, prompt)
                onSaved()
            },
        ) { Text(stringResource(R.string.save)) }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id)
