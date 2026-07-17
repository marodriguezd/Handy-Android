package com.handy.app.ui.postprocess

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.HandyModalBottomSheet
import com.handy.app.ui.components.Spacing

/**
 * Sprint 26 — Prompt create/edit modal sheet. Hosted in
 * [HandyModalBottomSheet] (MD3-native, NOT BasicAlertDialog per the
 * Sprint 26 plan — the multi-line text field is too tall for a Dialog
 * without clipping).
 *
 * Stateless w.r.t. the visible-vs-hidden axis — the parent owns
 * `visible` and toggles it on save + ESC. Internally carries a tiny
 * amount of edit-buffer state (the working name + text fields)
 * keyed by `initial?.id` so a fresh sheet on a different prompt
 * resets correctly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptEditor(
    visible: Boolean,
    initial: PostProcessPrompt?,
    onSave: (PostProcessPrompt) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var text by remember(initial?.id) { mutableStateOf(initial?.text ?: "") }

    HandyModalBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.lg)) {
            Text(
                text = stringResource(
                    if (initial == null) R.string.postprocess_prompt_editor_title_new
                    else R.string.postprocess_prompt_editor_title_edit,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.postprocess_prompt_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.postprocess_prompt_text_label)) },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = {
                    val id = initial?.id ?: java.util.UUID.randomUUID().toString()
                    onSave(PostProcessPrompt(id, name.trim(), text.trim()))
                    onDismiss()
                },
                enabled = name.isNotBlank() && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dialog_save))
            }
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}
