package com.handy.android

import android.content.Intent
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.android.ui.theme.HandyTheme

class PostProcessSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HandyTheme {
                PostProcessSettingsScreen(
                    onOpenCustomWords = {
                        startActivity(Intent(this, CustomWordsActivity::class.java))
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostProcessSettingsScreen(onOpenCustomWords: () -> Unit, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var processingEnabled by remember { mutableStateOf(SettingsManager.postProcessingEnabled(context)) }
    var capitalizationEnabled by remember { mutableStateOf(SettingsManager.autoCapitalizationEnabled(context)) }
    var punctuationEnabled by remember { mutableStateOf(SettingsManager.punctuationCleanupEnabled(context)) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.postprocess_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.postprocess_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingToggle(
                        title = stringResource(R.string.postprocess_title),
                        description = stringResource(R.string.postprocess_enable_description),
                        checked = processingEnabled,
                        onCheckedChange = {
                            processingEnabled = it
                            SettingsManager.setPostProcessingEnabled(context, it)
                        },
                    )
                    SettingToggle(
                        title = stringResource(R.string.postprocess_capitalize),
                        description = stringResource(R.string.postprocess_capitalize_description),
                        checked = capitalizationEnabled,
                        enabled = processingEnabled,
                        onCheckedChange = {
                            capitalizationEnabled = it
                            SettingsManager.setAutoCapitalizationEnabled(context, it)
                        },
                    )
                    SettingToggle(
                        title = stringResource(R.string.postprocess_punctuation),
                        description = stringResource(R.string.postprocess_punctuation_description),
                        checked = punctuationEnabled,
                        enabled = processingEnabled,
                        onCheckedChange = {
                            punctuationEnabled = it
                            SettingsManager.setPunctuationCleanupEnabled(context, it)
                        },
                    )
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenCustomWords,
            ) {
                Text(stringResource(R.string.postprocess_edit_words))
            }
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
