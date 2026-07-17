package com.handy.app.ui.postprocess

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.handy.app.R

/**
 * Sprint 26 — API-key field on the Post-Process destination.
 *
 * PasswordVisualTransformation by default with a Visibility toggle in
 * the trailing icon slot — same pattern as the existing SettingsScreen
 * ApiKey row (Sprint 23). The supporting text shifts between
 * "required" and "optional" per [PostProcessProvider.requiresApiKey]
 * so local-only providers (Ollama, Custom) don't gate on a blank
 * key. The [isError] flag is driven by the same flag.
 *
 * Stateless — the parent owns the value + persistence side-effect.
 */
@Composable
fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    provider: PostProcessProvider,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(stringResource(R.string.postprocess_apikey_label)) },
        placeholder = { Text(stringResource(R.string.postprocess_apikey_placeholder)) },
        supportingText = {
            Text(
                stringResource(
                    if (provider.requiresApiKey) R.string.postprocess_apikey_required
                    else R.string.postprocess_apikey_optional
                ),
            )
        },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(R.string.content_desc_toggle),
                )
            }
        },
        isError = provider.requiresApiKey && value.isBlank(),
        modifier = modifier.fillMaxWidth(),
    )
}
