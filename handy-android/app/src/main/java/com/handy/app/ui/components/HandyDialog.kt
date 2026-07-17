package com.handy.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

/**
 * MD3-native [AlertDialog] wrapper used by every confirmation flow
 * (delete model, delete history, paste-method migration, etc.).
 */
@Composable
fun HandyConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel)
            }
        },
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * MD3-native [BasicAlertDialog] wrapper for "model progress" and other
 * flows that need a custom content area instead of a simple text body.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandyBasicDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = contentColor,
        ) {
            content()
        }
    }
}

/**
 * MD3-native [AlertDialog] wrapper for informational dialogs (single
 * OK / acknowledge button). Used by the About "Licenses" disclosure
 * and the General settings Shizuku permission error flow.
 *
 * Distinct from [HandyConfirmDialog] which is for confirm/dismiss flows
 * with a destructive action; this primitive is for "show this message
 * and acknowledge" — no destructive choice.
 *
 *  - Title = `onSurface`
 *  - Body = `onSurfaceVariant`
 *  - Container = `surfaceContainerHigh` (matches the rest of M3 dialogs)
 *  - OK button label defaults to `R.string.dialog_ok` resolved via
 *    `stringResource`. Callers may override with a custom label.
 */
@Composable
fun HandyInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    okLabel: String = "OK",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = okLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
