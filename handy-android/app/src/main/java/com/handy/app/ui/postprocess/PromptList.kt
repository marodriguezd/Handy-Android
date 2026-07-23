package com.handy.app.ui.postprocess

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.handy.app.R
import com.handy.app.ui.components.Spacing

/**
 * Sprint 26 — A user-defined prompt template. Stored as
 * `<id>:<name>:<text>` triple so the post-process engine can
 * reference prompts by stable id across migrations.
 *
 * Held JVM-types-only so the form state stays easy to test; the
 * Compose layer above constructs/destructs these as needed.
 */
data class PostProcessPrompt(
    val id: String,
    val name: String,
    val text: String,
)

/**
 * Sprint 26 — Stateless prompt-list. Empty-state shows a friendly
 * placeholder; otherwise one row per prompt with edit + delete
 * IconButtons in the trailing slot. The "Add" affordance lives at
 * the bottom as a primary-colored TextButton-with-leading-icon
 * per the M3 spec for "add a new entry" patterns.
 *
 * The parent owns the [prompts] list + the [onAdd] / [onEdit] /
 * [onDelete] side-effects. The list itself is non-persistent; the
 * parent (PostProcessScreen) writes it to [com.handy.app.SettingsStore]
 * via a structured copy after every change.
 */
@Composable
fun PromptList(
    prompts: List<PostProcessPrompt>,
    activePromptId: String,
    onAdd: () -> Unit,
    onEdit: (PostProcessPrompt) -> Unit,
    onDelete: (PostProcessPrompt) -> Unit,
    onActivate: (PostProcessPrompt) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.postprocess_prompts_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onImport) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = stringResource(R.string.postprocess_prompt_import),
                    )
                }
                IconButton(onClick = onExport) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = stringResource(R.string.postprocess_prompt_export),
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            if (prompts.isEmpty()) {
                Text(
                    text = stringResource(R.string.postprocess_prompts_empty_state),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    prompts.forEach { prompt ->
                        PromptRow(
                            prompt = prompt,
                            isActive = prompt.id == activePromptId,
                            onEdit = { onEdit(prompt) },
                            onDelete = { onDelete(prompt) },
                            onActivate = { onActivate(prompt) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAdd)
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = stringResource(R.string.postprocess_prompt_add),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun PromptRow(
    prompt: PostProcessPrompt,
    isActive: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onActivate: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .clickable(onClick = onActivate),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isActive,
            onClick = onActivate,
        )
        Spacer(modifier = Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = prompt.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = prompt.text.take(80),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isActive) {
            Text(
                text = stringResource(R.string.postprocess_prompt_active_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.content_desc_edit),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.content_desc_delete),
            )
        }
    }
}
