package com.handy.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.handy.android.ui.theme.HandyTheme

class LlmSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HandyTheme { LlmSettingsScreen { finish() } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.llm_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onSaved) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.llm_settings_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingSwitch(
                        title = stringResource(R.string.llm_enable_label),
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                    OutlinedTextField(endpoint, { endpoint = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.llm_endpoint_label)) }, singleLine = true)
                    OutlinedTextField(
                        apiKey,
                        { apiKey = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.llm_api_key_label)) },
                        singleLine = true,
                        // Do not expose the API key in plain text (screen, recents preview, screenshots).
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.llm_model_label)) }, singleLine = true)
                    OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.llm_system_prompt_label)) }, minLines = 4)
                }
            }

            Text(stringResource(R.string.llm_prompt_library), style = MaterialTheme.typography.titleLarge)
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        templateName,
                        { templateName = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.llm_template_name)) },
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
                        ) { Text(stringResource(R.string.llm_save_template)) }
                        OutlinedButton(
                            enabled = templateName.isNotBlank(),
                            onClick = {
                                SettingsManager.deleteLlmPromptTemplate(context, templateName)
                                templates = SettingsManager.llmPromptTemplates(context)
                                templateName = ""
                            },
                        ) { Text(stringResource(R.string.delete)) }
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
                }
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


