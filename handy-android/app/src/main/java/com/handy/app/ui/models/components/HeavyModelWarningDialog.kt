package com.handy.app.ui.models.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handy.app.R
import com.handy.app.capability.CapabilitySnapshot
import com.handy.app.model.ModelInfo

@Composable
fun HeavyModelWarningDialog(
    model: ModelInfo,
    snapshot: CapabilitySnapshot,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var understood by remember { mutableStateOf(false) }
    val isExtreme = model.sizeBytes > 3L * 1024 * 1024 * 1024
    val sizeGb = "%.1f".format(model.sizeBytes / 1_073_741_824.0)
    val totalGb = "%.1f".format(snapshot.totalMemGbReport)

    val titleRes = if (isExtreme) R.string.heavy_dialog_title_extreme else R.string.heavy_dialog_title
    val bodyRes = when {
        isExtreme -> R.string.heavy_dialog_body_extreme
        else -> R.string.heavy_dialog_body
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(titleRes))
            }
        },
        text = {
            Column {
                Text(
                    text = stringResource(bodyRes, model.displayName, sizeGb, totalGb),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = understood,
                        onCheckedChange = { understood = it },
                    )
                    Text(
                        text = stringResource(R.string.heavy_dialog_consent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = understood,
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.models_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}
