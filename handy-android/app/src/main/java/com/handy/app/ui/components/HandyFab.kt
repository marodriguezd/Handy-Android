package com.handy.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * MD3-native [FloatingActionButton].  Reusable across Android; defaults
 * to the record-mic icon (the flagship Handy action is "start dictation"
 * from the Models screen & re-engage the IME).
 */
@Composable
@Suppress("ModifierParameter")
fun HandyFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Start dictation",
        )
    }
}

/**
 * MD3-native extended FAB with a label + icon.  Use on the Models screen
 * for "Use this model" and on the History screen for "Open recordings".
 */
@Composable
@Suppress("ModifierParameter")
fun HandyExtendedFab(
    label: String,
    icon: ImageVector = Icons.Default.Mic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        text = { Text(text = label) },
        icon = {
            Icon(imageVector = icon, contentDescription = null)
        },
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}
